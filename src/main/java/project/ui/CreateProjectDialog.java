package project.ui;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.Environment;
import app.SwingUtils;
import app.config.Options;
import dev.kdl.parse.KdlParseException;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import net.miginfocom.swing.MigLayout;
import project.Project;
import project.ProjectListing;
import util.Logger;

public class CreateProjectDialog extends JDialog
{
	private ProjectListing result = null;

	private JTextField nameField;
	private JTextField idField;
	private JTextField pathField;
	private JButton createButton;

	private boolean idManuallyEdited = false;
	private File browsedDir = null;

	/**
	 * Shows the dialog and returns the created project, or null if cancelled.
	 */
	public static ProjectListing showDialog(JFrame parent)
	{
		CreateProjectDialog dialog = new CreateProjectDialog(parent);
		dialog.setVisible(true);
		return dialog.result;
	}

	private CreateProjectDialog(JFrame parent)
	{
		super(parent);

		nameField = new JTextField();
		nameField.setMargin(SwingUtils.TEXTBOX_INSETS);

		idField = new JTextField();
		idField.setMargin(SwingUtils.TEXTBOX_INSETS);

		pathField = new JTextField();
		pathField.setMargin(SwingUtils.TEXTBOX_INSETS);
		pathField.setEditable(false);

		// Auto-generate ID from Name
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onNameChanged();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onNameChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onNameChanged();
			}
		});

		// Track manual edits to ID
		idField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				onIdChanged();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onIdChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onIdChanged();
			}
		});

		// Browse button
		JButton browseButton = new JButton("Browse...");
		SwingUtils.addBorderPadding(browseButton);
		browseButton.addActionListener(e -> browseForPath());

		// Create and Cancel buttons
		createButton = new JButton("Create");
		SwingUtils.addBorderPadding(createButton);
		createButton.setEnabled(false);
		createButton.addActionListener(e -> createProject());

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener(e -> setVisible(false));

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				setVisible(false);
			}
		});

		setLayout(new MigLayout("ins 16, wrap", "[grow]"));

		JLabel nameLabel = new JLabel("Name");
		add(nameLabel, "");
		add(nameField, "growx");

		JLabel idLabel = new JLabel("ID");
		JLabel idDesc = new JLabel("Unique internal identifier");
		idDesc.setForeground(SwingUtils.getGrayTextColor());
		SwingUtils.setFontSize(idDesc, 11);
		add(idLabel, "split 2, gaptop 8");
		add(idDesc, "ax right, pushx");
		add(idField, "growx");

		JLabel pathLabel = new JLabel("Path");
		add(pathLabel, "gaptop 8");
		add(pathField, "split 2, growx");
		add(browseButton, "");

		add(new JPanel(), "growx, sg but, split 3, gaptop 12");
		add(createButton, "growx, sg but");
		add(cancelButton, "growx, sg but");

		updatePath();
		validate_();

		pack();
		setResizable(false);

		setTitle("New Project");
		setIconImage(Environment.getDefaultIconImage());
		setLocationRelativeTo(parent);
		setModal(true);
		nameField.requestFocusInWindow();

		SwingUtilities.invokeLater(() -> {
			getRootPane().setDefaultButton(createButton);
			nameField.requestFocusInWindow();
		});
	}

	private boolean updatingId = false;

	private void onNameChanged()
	{
		if (!idManuallyEdited) {
			updatingId = true;
			idField.setText(toSnakeCase(nameField.getText()));
			updatingId = false;
		}
		updatePath();
		validate_();
	}

	private void onIdChanged()
	{
		if (!updatingId) {
			idManuallyEdited = !idField.getText().isEmpty();
		}
		updatePath();
		validate_();
	}

	private void updatePath()
	{
		String id = getEffectiveId();
		File dir;

		if (browsedDir != null) {
			dir = id.isEmpty() ? browsedDir : new File(browsedDir, id);
		}
		else {
			File projectsDir = getDefaultProjectsDir();
			dir = id.isEmpty() ? projectsDir : new File(projectsDir, id);
		}

		pathField.setText(abbreviateHome(dir.getAbsolutePath()));
	}

	private String getEffectiveId()
	{
		String id = idField.getText().trim();
		if (id.isEmpty())
			return toSnakeCase(nameField.getText());
		return id;
	}

	private File getProjectPath()
	{
		String id = getEffectiveId();
		if (browsedDir != null) {
			return id.isEmpty() ? browsedDir : new File(browsedDir, id);
		}
		File projectsDir = getDefaultProjectsDir();
		return id.isEmpty() ? projectsDir : new File(projectsDir, id);
	}

	private void browseForPath()
	{
		DirChooser dirChooser = new DirChooser(getDefaultProjectsDir(), "Select Project Location");
		if (dirChooser.prompt() == ChooseDialogResult.APPROVE) {
			File selected = dirChooser.getSelectedFile();
			String[] contents = selected.list();
			if (contents != null && contents.length > 0) {
				// Directory has files, use it as parent
				browsedDir = selected;
			}
			else {
				// Empty directory, use it directly
				browsedDir = selected.getParentFile();
				// If the selected dir name matches the id, just use parent as browsedDir
				String id = getEffectiveId();
				if (!selected.getName().equals(id)) {
					browsedDir = selected;
				}
			}
			updatePath();
			validate_();
		}
	}

	private void validate_()
	{
		String name = nameField.getText().trim();
		String id = getEffectiveId();
		File path = getProjectPath();

		String error = null;

		if (name.isEmpty()) {
			error = "Enter a project name";
		}
		else if (id.isEmpty()) {
			error = "Enter a project ID";
		}
		else if (!id.matches("[a-z]*[a-z0-9_]*")) {
			error = "ID must contain only lowercase letters, digits, and underscores, and must start with a letter";
		}
		else if (new File(path, "project.kdl").exists()) {
			error = "A project already exists at this location";
		}

		if (error != null) {
			createButton.setToolTipText(error);
			createButton.setEnabled(false);
		}
		else {
			createButton.setToolTipText(null);
			createButton.setEnabled(true);
		}
	}

	private void createProject()
	{
		File path = getProjectPath();
		String id = getEffectiveId();
		String name = nameField.getText().trim();

		try {
			result = Project.create(path, "blank", id, name);
			setVisible(false);
		}
		catch (IOException | KdlParseException e) {
			Logger.logError("Failed to create project: " + e.getMessage());
			Environment.showErrorMessage("Failed to create project", "%s", e.getMessage());
		}
	}

	private static File getDefaultProjectsDir()
	{
		String configured = Environment.mainConfig.getString(Options.ProjectsDir);
		if (configured != null && !configured.isEmpty()) {
			return new File(configured);
		}

		File docs = Environment.getUserDocumentsDir();
		String subdir = Environment.isLinux() ? "starrod" : "Star Rod";
		return new File(docs, subdir);
	}

	static String toSnakeCase(String input)
	{
		return input.trim()
			.toLowerCase()
			.replaceAll("[^a-z0-9]+", "_")
			.replaceAll("_+", "_")
			.replaceAll("^_|_$", "");
	}

	private static String abbreviateHome(String path)
	{
		String home = System.getProperty("user.home");
		if (home != null && path.startsWith(home)) {
			return "~" + path.substring(home.length());
		}
		return path;
	}
}
