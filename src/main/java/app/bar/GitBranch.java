package app.bar;

import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import app.Environment;
import util.Logger;
import util.ui.ThemedIcon;

/** Displays the current git branch and watches for changes. */
public class GitBranch extends JPanel
{
	private final JLabel label;
	private WatchService watchService;
	private Thread watchThread;

	public GitBranch()
	{
		setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));
		setOpaque(false);

		label = new JLabel();
		label.setIcon(ThemedIcon.GIT_BRANCH.derive(15, 15));
		label.setIconTextGap(4);

		add(label);

		updateGitBranch();
		startWatching();
	}

	private void updateGitBranch()
	{
		File projectDir = Environment.getProject().getDirectory();
		File gitDir = new File(projectDir, ".git");

		if (!gitDir.exists()) {
			setVisible(false);
			return;
		}

		try {
			// Use symbolic-ref which works even without commits
			Process process = new ProcessBuilder("git", "symbolic-ref", "--short", "HEAD")
				.directory(projectDir)
				.start();

			String branch = null;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				branch = reader.readLine();
			}

			int exitCode = process.waitFor();

			if (exitCode == 0 && branch != null && !branch.isEmpty()) {
				label.setText(branch);
				setVisible(true);
			}
			else {
				setVisible(false);
			}
		}
		catch (Exception e) {
			Logger.logWarning("Failed to get git branch: " + e.getMessage());
			setVisible(false);
		}
	}

	/** Refresh the git branch display. */
	public void refresh()
	{
		updateGitBranch();
	}

	private void startWatching()
	{
		File projectDir = Environment.getProject().getDirectory();
		File gitDir = new File(projectDir, ".git");

		if (!gitDir.exists())
			return;

		try {
			watchService = FileSystems.getDefault().newWatchService();
			Path gitPath = gitDir.toPath();

			// Watch .git directory for changes to HEAD file
			gitPath.register(watchService,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_CREATE);

			watchThread = new Thread(() -> {
				try {
					while (!Thread.currentThread().isInterrupted()) {
						WatchKey key = watchService.take();

						for (WatchEvent<?> event : key.pollEvents()) {
							Path changed = (Path) event.context();
							if (changed.toString().equals("HEAD")) {
								// Update on Swing thread
								SwingUtilities.invokeLater(this::updateGitBranch);
							}
						}

						if (!key.reset())
							break;
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}, "GitBranchWatcher");

			watchThread.setDaemon(true);
			watchThread.start();
		}
		catch (IOException e) {
			Logger.logWarning("Failed to start git branch watcher: " + e.getMessage());
		}
	}

	/** Stop watching for git changes. Call when disposing. */
	public void dispose()
	{
		if (watchThread != null) {
			watchThread.interrupt();
			watchThread = null;
		}

		if (watchService != null) {
			try {
				watchService.close();
			}
			catch (IOException e) {
				Logger.logWarning("Error closing watch service: " + e.getMessage());
			}
			watchService = null;
		}
	}
}
