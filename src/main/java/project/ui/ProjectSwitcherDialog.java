package project.ui;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import app.Environment;
import app.StarRodFrame;
import app.SwingUtils;
import app.Themes;
import app.Themes.Theme;
import app.config.Options;
import dev.kdl.parse.KdlParseException;
import project.ProjectListing;
import project.ProjectManager;
import game.map.editor.ui.dialogs.ChooseDialogResult;
import game.map.editor.ui.dialogs.DirChooser;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.FilteredListModel;

/**
 * Window for selecting and managing Star Rod projects.
 * Similar to Godot's project manager.
 */
public class ProjectSwitcherDialog extends StarRodFrame
{
	private final String TAB_PROJECTS = "Projects";

	private JList<ProjectListing> list;
	private DefaultListModel<ProjectListing> listModel;
	private FilteredListModel<ProjectListing> filteredListModel;
	private JTextField filterTextField;
	private JPopupMenu contextMenu;
	private CardLayout cardLayout;
	private JPanel contentPanel;

	private final ProjectManager projectManager;
	private final DirChooser dirChooser;

	private final CountDownLatch latch = new CountDownLatch(1);
	private ProjectListing selectedProject = null;

	/**
	 * Shows the project switcher and returns the selected project listing.
	 * Blocks until the user makes a selection or closes the window.
	 * @return The selected project listing, or null if cancelled
	 */
	public static ProjectListing showPrompt()
	{
		ProjectSwitcherDialog window = new ProjectSwitcherDialog();
		window.setVisible(true);

		try {
			window.latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return window.selectedProject;
	}

	private ProjectSwitcherDialog()
	{
		super("Star Rod Launcher");
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				selectedProject = null;
				latch.countDown();
				dispose();
			}
		});

		projectManager = ProjectManager.getInstance();
		dirChooser = new DirChooser(new File("."), "Select Project Directory");

		// Move title label to top header
		JLabel titleLabel = new JLabel("Star Rod");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

		// === SIDEBAR ===
		JPanel sidebar = new JPanel(new MigLayout("ins 16, fill, wrap, gapy 4", "[grow]"));
		sidebar.setPreferredSize(new Dimension(160, 0));

		// Tab buttons
		ButtonGroup tabGroup = new ButtonGroup();

		JToggleButton projectsTab = createTabButton("Projects");
		tabGroup.add(projectsTab);
		sidebar.add(projectsTab, "growx");

		// Spacer to push theme chooser to bottom
		sidebar.add(new JLabel(), "grow, pushy");

		// Theme chooser
		JLabel themeLabel = new JLabel("Theme");
		SwingUtils.setFontSize(themeLabel, 11);
		sidebar.add(themeLabel, "");

		JComboBox<Theme> themeCombo = new JComboBox<>();
		for (Theme theme : Themes.getThemes()) {
			themeCombo.addItem(theme);
		}
		themeCombo.setSelectedItem(Themes.getCurrentTheme());
		themeCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = new JLabel(value != null ? value.name : "");
			label.setOpaque(true);
			if (isSelected) {
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			else {
				label.setBackground(list.getBackground());
				label.setForeground(list.getForeground());
			}
			label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			return label;
		});
		themeCombo.addActionListener(e -> {
			Theme selected = (Theme) themeCombo.getSelectedItem();
			if (selected != null) {
				Themes.setTheme(selected);
				Environment.mainConfig.setString(Options.Theme, selected.key);
				Environment.mainConfig.saveConfigFile();
				SwingUtilities.updateComponentTreeUI(this);
			}
		});
		sidebar.add(themeCombo, "growx");

		// === CONTENT AREA ===
		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);

		// Projects panel
		JPanel projectsPanel = createProjectsPanel();
		contentPanel.add(projectsPanel, TAB_PROJECTS);

		// Tab button actions
		projectsTab.addActionListener(e -> cardLayout.show(contentPanel, TAB_PROJECTS));
		// Eventually add other tabs like Templates and Learn

		// Select projects tab by default
		projectsTab.setSelected(true);

		// === MAIN LAYOUT ===
		// Add a top row for the title, then the main content row
		setLayout(new MigLayout("ins 0, fill", "[160][grow]", "[]0[grow]"));
		add(titleLabel, "span 2, center, wrap");
		add(sidebar, "growy");
		add(contentPanel, "grow");

		setPreferredSize(new Dimension(800, 500));
		setMinimumSize(new Dimension(600, 400));
		pack();
		setLocationRelativeTo(null);

		// Cmd+K / Ctrl+K to focus search
		int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		KeyStroke searchKey = KeyStroke.getKeyStroke(KeyEvent.VK_K, shortcutMask);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(searchKey, "focusSearch");
		getRootPane().getActionMap().put("focusSearch", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				filterTextField.requestFocusInWindow();
				filterTextField.selectAll();
			}
		});
	}

	private JToggleButton createTabButton(String text)
	{
		JToggleButton button = new JToggleButton(text);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setFocusPainted(false);
		button.putClientProperty("JButton.buttonType", "borderless");
		return button;
	}

	private JPanel createProjectsPanel()
	{
		JPanel panel = new JPanel(new MigLayout("ins 16, fill, wrap"));

		// Load projects
		listModel = new DefaultListModel<ProjectListing>();
		refreshProjectList();

		// Create list
		list = new JList<>();
		list.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		list.setCellRenderer(new ProjectCellRenderer());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		filteredListModel = new FilteredListModel<>(listModel);
		list.setModel(filteredListModel);

		// Context menu for list items
		contextMenu = new JPopupMenu();

		JMenuItem removeItem = new JMenuItem("Remove from List");
		removeItem.addActionListener(e -> removeSelectedProject());
		contextMenu.add(removeItem);

		JMenuItem deleteItem = new JMenuItem("Delete Permanently");
		deleteItem.addActionListener(e -> deleteSelectedProjectFromDisk());
		contextMenu.add(deleteItem);

		// Double-click to open, right-click for context menu
		list.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && list.getSelectedValue() != null) {
					openSelectedProject();
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				handlePopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				handlePopup(e);
			}

			private void handlePopup(MouseEvent e)
			{
				if (e.isPopupTrigger()) {
					int index = list.locationToIndex(e.getPoint());
					if (index >= 0) {
						list.setSelectedIndex(index);
						contextMenu.show(list, e.getX(), e.getY());
					}
				}
			}
		});

		// Delete key to remove, Enter to open
		list.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_DELETE) {
					removeSelectedProject();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null) {
					openSelectedProject();
				}
			}
		});

		// Filter text field
		filterTextField = new JTextField(20);
		filterTextField.setMargin(SwingUtils.TEXTBOX_INSETS);
		filterTextField.putClientProperty("JTextField.placeholderText", "Search...");
		filterTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateListFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateListFilter();
			}
		});
		SwingUtils.addBorderPadding(filterTextField);

		JScrollPane listScrollPane = new JScrollPane(list);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		listScrollPane.setWheelScrollingEnabled(true);

		// Buttons
		JButton createButton = new JButton("New Project");
		createButton.addActionListener(e -> {
			ProjectListing newProject = CreateProjectDialog.showDialog(this);
			if (newProject != null) {
				projectManager.recordProjectOpened(newProject);
				refreshProjectList();
				updateListFilter();
				list.setSelectedValue(newProject, true);
				openSelectedProject();
			}
		});

		JButton browseButton = new JButton("Browse...");
		browseButton.addActionListener(e -> browseForProject());

		// Layout
		panel.add(filterTextField, "split 3, growx, pushx");
		panel.add(browseButton, "sg but");
		panel.add(createButton, "sg but, wrap");

		panel.add(listScrollPane, "grow, push");

		// Set New Project as the default button so it looks like the primary action
		SwingUtilities.invokeLater(() -> getRootPane().setDefaultButton(createButton));

		return panel;
	}

	private void openURL(String url)
	{
		try {
			Desktop.getDesktop().browse(new URI(url));
		}
		catch (Exception ex) {
			Logger.logError("Failed to open URL: " + url);
		}
	}

	private void refreshProjectList()
	{
		listModel.clear();
		List<ProjectListing> projects = projectManager.getRecentProjects();
		for (ProjectListing project : projects) {
			listModel.addElement(project);
		}
	}

	private void updateListFilter()
	{
		filteredListModel.setFilter(element -> {
			ProjectListing project = (ProjectListing) element;
			String filterText = filterTextField.getText().toUpperCase();
			String name = project.getName().toUpperCase();
			String path = project.getPath().toUpperCase();
			return name.contains(filterText) || path.contains(filterText);
		});
	}

	private void openSelectedProject()
	{
		ProjectListing selected = list.getSelectedValue();
		if (selected == null) {
			return;
		}

		selectedProject = selected;
		latch.countDown();
		dispose();
	}

	private void browseForProject()
	{
		if (dirChooser.prompt() == ChooseDialogResult.APPROVE) {
			File selectedDir = dirChooser.getSelectedFile();
			ProjectListing newProject;
			try {
				newProject = new ProjectListing(selectedDir);
			} catch (IOException | KdlParseException e) {
				Environment.showErrorMessage("Failed to open project", "The folder you selected is not a valid project: %s", e.getMessage());
				return;
			}
			projectManager.recordProjectOpened(newProject);

			// Refresh and select the new project
			refreshProjectList();
			updateListFilter();
			list.setSelectedValue(newProject, true);

			// Open it immediately
			openSelectedProject();
		}
	}

	private void removeSelectedProject()
	{
		ProjectListing selected = list.getSelectedValue();
		if (selected == null) {
			return;
		}

		projectManager.removeFromHistory(selected);
		refreshProjectList();
		updateListFilter();

		if (filteredListModel.getSize() > 0) {
			list.setSelectedIndex(0);
		}
	}

	private void deleteSelectedProjectFromDisk()
	{
		ProjectListing selected = list.getSelectedValue();
		if (selected == null) {
			return;
		}

		boolean success = projectManager.deleteFromDisk(selected);
		if (!success) {
			SwingUtils.getErrorDialog()
				.setTitle("Delete Failed")
				.setMessage("Failed to delete project files.")
				.show();
		}

		refreshProjectList();
		updateListFilter();

		if (filteredListModel.getSize() > 0) {
			list.setSelectedIndex(0);
		}
	}
}
