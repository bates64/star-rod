package app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultCaret;

import app.build.BuildEnvironment;
import app.build.BuildException;
import app.build.BuildOutputListener;
import app.build.BuildResult;
import app.build.NixEnvironment;
import app.build.WslNixOsEnvironment;
import net.miginfocom.swing.MigLayout;

/**
 * Dialog that displays build output in real-time with a progress bar.
 */
public class BuildOutputDialog extends JDialog
{
	public static final String NINJA_STATUS = "NINJA %P "; // Percentage time remaining estimate
	private static final Pattern NINJA_PROGRESS = Pattern.compile("NINJA +(\\d+)% (.*)");

	private final JTextArea outputArea;
	private final JProgressBar progressBar;
	private final JButton cancelButton;
	private final JButton closeButton;

	private BuildEnvironment buildEnv;
	private boolean buildComplete = false;

	public BuildOutputDialog(Frame parent)
	{
		super(parent, "Building...", false);

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e)
			{
				handleClose();
			}
		});

		outputArea = new JTextArea();
		outputArea.setEditable(false);
		outputArea.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
		outputArea.setRows(25);
		outputArea.setColumns(100);

		// Enable auto-scroll
		DefaultCaret caret = (DefaultCaret) outputArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		JScrollPane scrollPane = new JScrollPane(outputArea);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		progressBar = new JProgressBar(0, 100);
		progressBar.setIndeterminate(true);
		progressBar.setStringPainted(true);
		progressBar.setString("Configuring...");

		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> handleCancel());

		closeButton = new JButton("Close");
		closeButton.setEnabled(false);
		closeButton.addActionListener(e -> dispose());

		setLayout(new MigLayout("fill, ins 8", "[grow]", "[grow][pref!][pref!]"));
		add(scrollPane, "grow, wrap");
		add(progressBar, "growx, wrap 8");
		add(cancelButton, "split 2, align right");
		add(closeButton, "align right");

		pack();
		setMinimumSize(new Dimension(600, 400));
		setLocationRelativeTo(parent);
	}

	/**
	 * Starts the build process asynchronously.
	 */
	public void startBuild()
	{
		setVisible(true);

		// Create build environment on background thread
		Environment.getExecutor().submit(() -> {
			try {
				if (Environment.isWindows()) {
					buildEnv = new WslNixOsEnvironment();
				}
				else {
					buildEnv = new NixEnvironment();
				}

				try {
					buildEnv.configure(getOutputListener());
				}
				catch (IOException | BuildException e) {
					SwingUtilities.invokeLater(() -> {
						appendOutput("Configuration error: " + e.getMessage(), true);
						handleBuildError(e);
					});
					return;
				}

				buildEnv.buildAsync(getOutputListener()).thenAccept(result -> {
					SwingUtilities.invokeLater(() -> handleBuildComplete(result));
				}).exceptionally(ex -> {
					SwingUtilities.invokeLater(() -> handleBuildError(ex));
					return null;
				});
			}
			catch (BuildException e) {
				SwingUtilities.invokeLater(() -> {
					if (!e.isSilent()) {
						appendOutput("Build environment error: " + e.getMessage(), true);
						handleBuildError(e);
					}
					else {
						appendOutput(e.getMessage(), false);
						handleBuildComplete(BuildResult.cancelled(java.time.Duration.ZERO));
					}
				});
			}
		});
	}

	private BuildOutputListener getOutputListener()
	{
		return (line, isError) -> {
			SwingUtilities.invokeLater(() -> {
				if (!parseProgress(line)) {
					appendOutput(line, isError);
				}
			});
		};
	}

	private void appendOutput(String line, boolean isError)
	{
		if (isError) {
			// For errors, we could style differently but JTextArea doesn't support that easily
			outputArea.append(line + "\n");
		}
		else {
			outputArea.append(line + "\n");
		}
	}

	private boolean parseProgress(String line)
	{
		Matcher m = NINJA_PROGRESS.matcher(line);
		if (m.find()) {
			int percent = Integer.parseInt(m.group(1));
			String description = m.group(2);

			progressBar.setIndeterminate(false);
			progressBar.setValue(percent);
			progressBar.setString(description);

			return true;
		}
		return false;
	}

	private void handleBuildComplete(BuildResult result)
	{
		buildComplete = true;
		cancelButton.setEnabled(false);
		closeButton.setEnabled(true);
		progressBar.setIndeterminate(false);

		if (buildEnv != null) {
			buildEnv = null;
		}

		switch (result.getStatus()) {
			case SUCCESS:
				setTitle("Build Complete");
				progressBar.setValue(100);
				progressBar.setString("Build Successful");
				progressBar.setForeground(new Color(0, 150, 0));

				result.getOutputRom().ifPresent(rom -> {
					appendOutput("\n=== Build completed successfully ===", false);
					appendOutput("ROM: " + rom.getAbsolutePath(), false);
				});
				break;

			case FAILURE:
				setTitle("Build Failed");
				progressBar.setValue(0);
				progressBar.setString("Build Failed");
				progressBar.setForeground(Color.RED);

				result.getErrorMessage().ifPresent(msg -> {
					appendOutput("\n=== Build failed ===", true);
					appendOutput(msg, true);
				});
				break;

			case CANCELLED:
				setTitle("Build Cancelled");
				progressBar.setValue(0);
				progressBar.setString("Cancelled");
				appendOutput("\n=== Build cancelled ===", false);
				break;
		}
	}

	private void handleBuildError(Throwable ex)
	{
		buildComplete = true;
		cancelButton.setEnabled(false);
		closeButton.setEnabled(true);
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		progressBar.setString("Error");
		progressBar.setForeground(Color.RED);

		if (buildEnv != null) {
			buildEnv = null;
		}

		setTitle("Build Error");
		appendOutput("\n=== Build error ===", true);
		appendOutput(ex.getMessage(), true);

		if (!(ex instanceof BuildException) || !((BuildException) ex).isSilent()) {
			SwingUtils.getErrorDialog()
				.setTitle("Build Error")
				.setMessage(ex.getMessage())
				.show();
		}
	}

	private void handleCancel()
	{
		if (buildEnv != null && !buildComplete) {
			appendOutput("\nCancelling build...", false);
			buildEnv.cancel();
		}
	}

	private void handleClose()
	{
		if (!buildComplete && buildEnv != null) {
			handleCancel();
		}
		else {
			dispose();
		}
	}

	@Override
	public void dispose()
	{
		super.dispose();
	}
}
