package app;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import app.bar.Bar;
import app.input.InvalidInputException;
import app.pane.Dock;
import app.pane.Pane;
import assets.Asset;
import assets.AssetManager;
import assets.ExpectedAsset;
import common.BaseEditor;
import game.globals.editor.GlobalsEditor;
import game.map.Map;
import game.map.compiler.BuildException;
import game.map.compiler.CollisionCompiler;
import game.map.compiler.GeometryCompiler;
import game.map.editor.MapEditor;
import game.map.scripts.ScriptGenerator;
import game.message.editor.MessageEditor;
import game.sprite.editor.SpriteEditor;
import game.texture.editor.ImageEditor;
import game.worldmap.WorldMapEditor;
import net.miginfocom.swing.MigLayout;
import tools.SwingInspectorKt;
import util.Logger;

public class StarRodMain extends StarRodFrame
{
	// Layout constants
	private static final int MIN_PANE_WIDTH = 250;
	private static final int MIN_WINDOW_WIDTH = MIN_PANE_WIDTH * 3 + 300;
	private static final int MIN_WINDOW_HEIGHT = 600;
	private static final int MIN_DOCK_HEIGHT = 140;

	public static void main(String[] args) throws InterruptedException
	{
		// Handle --remote flag without initializing environment
		if (args.length > 0 && args[0].equalsIgnoreCase("--remote")) {
			String[] remoteArgs = new String[args.length - 1];
			System.arraycopy(args, 1, remoteArgs, 0, remoteArgs.length);
			SwingInspectorKt.main(remoteArgs);
			return;
		}

		boolean isCommandLine = args.length > 0 || GraphicsEnvironment.isHeadless();

		if (isCommandLine) {
			Environment.initialize(true);
			runCommandLine(args);
			Environment.exit();
		}
		else {
			Environment.initialize(false);
			LoadingBar.dismiss();
			new StarRodMain();
		}
	}

	private boolean taskRunning = false;

	private List<JButton> buttons = new ArrayList<>();

	private StarRodMain()
	{
		setTitle(Environment.decorateTitle("Star Rod"));
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT));

		Color bg = UIManager.getColor("Panel.background");
		getContentPane().setBackground(bg.darker());

		// Menu bar
		JMenuBar menuBar = new JMenuBar();
		menuBar.getAccessibleContext().setAccessibleName("menuBar");
		JMenu fileMenu = new JMenu("File");
		fileMenu.getAccessibleContext().setAccessibleName("fileMenu");
		JMenu editMenu = new JMenu("Edit");
		editMenu.getAccessibleContext().setAccessibleName("editMenu");
		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		setJMenuBar(menuBar);

		// TODO: click this to change project
		JLabel projectIdLabel = new JLabel(Environment.getProject().getManifest().getId());
		SwingUtils.setFontSize(projectIdLabel, 11);
		projectIdLabel.getAccessibleContext().setAccessibleName("projectIdLabel");

		JButton mapEditorButton = new JButton("Map Editor");
		trySetIcon(mapEditorButton, ExpectedAsset.ICON_MAP_EDITOR);
		SwingUtils.setFontSize(mapEditorButton, 12);
		mapEditorButton.getAccessibleContext().setAccessibleName("mapEditorButton");
		mapEditorButton.addActionListener((e) -> {
			action_openMapEditor();
		});
		buttons.add(mapEditorButton);

		JButton spriteEditorButton = new JButton("Sprite Editor");
		trySetIcon(spriteEditorButton, ExpectedAsset.ICON_SPRITE_EDITOR);
		SwingUtils.setFontSize(spriteEditorButton, 12);
		spriteEditorButton.getAccessibleContext().setAccessibleName("spriteEditorButton");
		spriteEditorButton.addActionListener((e) -> {
			action_openSpriteEditor();
		});
		buttons.add(spriteEditorButton);

		JButton msgEditorButton = new JButton("Message Editor");
		trySetIcon(msgEditorButton, ExpectedAsset.ICON_MSG_EDITOR);
		SwingUtils.setFontSize(msgEditorButton, 12);
		msgEditorButton.getAccessibleContext().setAccessibleName("messageEditorButton");
		msgEditorButton.addActionListener((e) -> {
			action_openMessageEditor();
		});
		buttons.add(msgEditorButton);

		JButton globalsEditorButton = new JButton("Globals Editor");
		trySetIcon(globalsEditorButton, ExpectedAsset.ICON_GLOBALS_EDITOR);
		SwingUtils.setFontSize(globalsEditorButton, 12);
		globalsEditorButton.getAccessibleContext().setAccessibleName("globalsEditorButton");
		globalsEditorButton.addActionListener((e) -> {
			action_openGlobalsEditor();
		});
		buttons.add(globalsEditorButton);

		JButton worldEditorButton = new JButton("World Map Editor");
		trySetIcon(worldEditorButton, ExpectedAsset.ICON_WORLD_EDITOR);
		SwingUtils.setFontSize(worldEditorButton, 12);
		worldEditorButton.getAccessibleContext().setAccessibleName("worldMapEditorButton");
		worldEditorButton.addActionListener((e) -> {
			action_openWorldMapEditor();
		});
		buttons.add(worldEditorButton);

		JButton imageEditorButton = new JButton("Image Editor");
		trySetIcon(imageEditorButton, ExpectedAsset.ICON_IMAGE_EDITOR);
		SwingUtils.setFontSize(imageEditorButton, 12);
		imageEditorButton.getAccessibleContext().setAccessibleName("imageEditorButton");
		imageEditorButton.addActionListener((e) -> {
			action_openImageEditor();
		});
		buttons.add(imageEditorButton);

		JButton themesMenuButton = new JButton("Choose Theme");
		trySetIcon(themesMenuButton, ExpectedAsset.ICON_THEMES);
		SwingUtils.setFontSize(themesMenuButton, 12);
		themesMenuButton.getAccessibleContext().setAccessibleName("themesMenuButton");
		themesMenuButton.addActionListener((e) -> {
			action_openThemesMenu();
		});
		buttons.add(themesMenuButton);

		// Build Project button
		JButton buildProjectButton = new JButton("Build Project");
		trySetIcon(buildProjectButton, ExpectedAsset.ICON_GOLD);
		SwingUtils.setFontSize(buildProjectButton, 12);
		buildProjectButton.getAccessibleContext().setAccessibleName("buildProjectButton");
		buildProjectButton.addActionListener((e) -> {
			action_buildProject();
		});
		buttons.add(buildProjectButton);

		// Open directories buttons
		JButton openConfigDirButton = new JButton("Open Config Dir");
		trySetIcon(openConfigDirButton, ExpectedAsset.ICON_SILVER);
		SwingUtils.setFontSize(openConfigDirButton, 12);
		openConfigDirButton.getAccessibleContext().setAccessibleName("openConfigDirButton");
		openConfigDirButton.addActionListener((e) -> {
			action_openDir(Environment.getUserConfigDir());
		});
		buttons.add(openConfigDirButton);

		JButton openProjectDirButton = new JButton("Open Project Dir");
		trySetIcon(openProjectDirButton, ExpectedAsset.ICON_GOLD);
		SwingUtils.setFontSize(openProjectDirButton, 12);
		openProjectDirButton.getAccessibleContext().setAccessibleName("openProjectDirButton");
		openProjectDirButton.addActionListener((e) -> {
			action_openDir(Environment.getProjectDirectory());
		});
		buttons.add(openProjectDirButton);

		// Window close handling
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				int choice = JOptionPane.OK_OPTION;

				if (taskRunning)
					choice = SwingUtils.getConfirmDialog()
						.setTitle("Task Still Running")
						.setMessage("A task is still running.", "Are you sure you want to exit?")
						.setMessageType(JOptionPane.WARNING_MESSAGE)
						.setOptionsType(JOptionPane.YES_NO_OPTION)
						.choose();

				if (choice == JOptionPane.OK_OPTION) {
					dispose();
					Environment.exit();
				}
			}
		});

		// Left pane - buttons panel
		Pane leftPane = new Pane();
		leftPane.setLayout(new MigLayout("fill, ins 8, wrap 1"));
		leftPane.getAccessibleContext().setAccessibleName("leftPane");

		JPanel buttonsPanel = new JPanel(new MigLayout("fillx, wrap 1, hidemode 3"));
		buttonsPanel.getAccessibleContext().setAccessibleName("buttonsPanel");
		buttonsPanel.add(mapEditorButton, "growx");
		buttonsPanel.add(spriteEditorButton, "growx");
		buttonsPanel.add(globalsEditorButton, "growx");
		buttonsPanel.add(msgEditorButton, "growx");
		buttonsPanel.add(worldEditorButton, "growx");
		buttonsPanel.add(imageEditorButton, "growx");
		buttonsPanel.add(themesMenuButton, "growx");
		buttonsPanel.add(openConfigDirButton, "growx");
		buttonsPanel.add(openProjectDirButton, "growx");
		buttonsPanel.add(buildProjectButton, "growx, gaptop 8");

		leftPane.add(buttonsPanel, "growx");
		leftPane.setMinimumSize(new Dimension(MIN_PANE_WIDTH, 0));

		// Middle pane - placeholder for now
		Pane middlePane = new Pane();
		middlePane.setLayout(new MigLayout("fill, ins 8"));
		middlePane.getAccessibleContext().setAccessibleName("middlePane");
		middlePane.add(new JLabel("Middle Pane"), "center");
		middlePane.setMinimumSize(new Dimension(MIN_PANE_WIDTH, 0));

		// Right pane - placeholder for now
		Pane rightPane = new Pane();
		rightPane.setLayout(new MigLayout("fill, ins 8"));
		rightPane.getAccessibleContext().setAccessibleName("rightPane");
		rightPane.add(new JLabel("Right Pane"), "center");
		rightPane.setMinimumSize(new Dimension(MIN_PANE_WIDTH, 0));

		// Dock (bottom panel in middle column)
		Dock dock = new Dock();
		dock.getAccessibleContext().setAccessibleName("dock");
		dock.setMinimumSize(new Dimension(0, MIN_DOCK_HEIGHT));

		// Create vertical split pane (middlePane | dock) for center column
		JSplitPane middleDockSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, middlePane, dock);
		middleDockSplit.setOneTouchExpandable(false);
		middleDockSplit.setDividerSize(4);
		middleDockSplit.setResizeWeight(1.0); // Give most space to middle pane
		middleDockSplit.setOpaque(false);

		// Create horizontal split panes (left | (middle + dock) | right)
		JSplitPane leftMiddleSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, middleDockSplit);
		leftMiddleSplit.setOneTouchExpandable(false);
		leftMiddleSplit.setDividerSize(4);
		leftMiddleSplit.setResizeWeight(0.0); // Left pane stays fixed, middle gets extra space
		leftMiddleSplit.setOpaque(false);

		JSplitPane mainHorizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftMiddleSplit, rightPane);
		mainHorizontalSplit.setOneTouchExpandable(false);
		mainHorizontalSplit.setDividerSize(4);
		mainHorizontalSplit.setResizeWeight(1.0); // Middle gets priority, right stays fixed
		mainHorizontalSplit.setOpaque(false);

		// Status bar
		Bar statusBar = new Bar();

		// Layout
		setLayout(new MigLayout("fill, ins 4, gap 4, wrap"));
		add(mainHorizontalSplit, "grow, push");
		add(statusBar, "growx, h 24!");

		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}

	public interface EditorWork
	{
		void execute() throws Exception;
	}

	private class EditorWorker extends SwingWorker<Boolean, String>
	{
		private final EditorWork work;

		private EditorWorker(EditorWork work)
		{
			this.work = work;

			setVisible(false);
			execute();
		}

		@Override
		protected Boolean doInBackground()
		{
			try {
				work.execute();
			}
			catch (Throwable t) {
				LoadingBar.dismiss();
				displayStackTrace(t);
			}
			return true;
		}

		@Override
		protected void done()
		{
			setVisible(true);
		}
	}

	private void action_openMapEditor()
	{
		new EditorWorker(() -> {
			MapEditor editor = new MapEditor(true);
			editor.launch();
		});
	}

	private void action_openSpriteEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new SpriteEditor();
			editor.launch();
		});
	}

	private void action_openMessageEditor()
	{
		new EditorWorker(() -> {
			MessageEditor editor = new MessageEditor();
			editor.launch();
		});
	}

	private void action_openGlobalsEditor()
	{
		new EditorWorker(() -> {
			CountDownLatch editorClosedSignal = new CountDownLatch(1);
			new GlobalsEditor(editorClosedSignal);
			editorClosedSignal.await();
		});
	}

	private void action_openWorldMapEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new WorldMapEditor();
			editor.launch();
		});
	}

	private void action_openImageEditor()
	{
		new EditorWorker(() -> {
			BaseEditor editor = new ImageEditor();
			editor.launch();
		});
	}

	private void action_openThemesMenu()
	{
		new EditorWorker(() -> {
			CountDownLatch editorClosedSignal = new CountDownLatch(1);
			new ThemesEditor(editorClosedSignal);
			editorClosedSignal.await();
		});
	}

	private void action_buildProject()
	{
		BuildOutputDialog dialog = new BuildOutputDialog(this);
		dialog.startBuild();
	}

	private void action_openDir(File dir)
	{
		if (dir.exists()) {
			try {
				Desktop desktop = Desktop.getDesktop();
				desktop.open(dir);
			}
			catch (IOException e) {
				Logger.printStackTrace(e);
				SwingUtils.getWarningDialog()
					.setTitle("IOException")
					.setMessage(e.getMessage())
					.show();
			}
		}
		else {
			Toolkit.getDefaultToolkit().beep();
			SwingUtils.getWarningDialog()
				.setTitle("Directory Not Found")
				.setMessage("Could not find:", dir.getAbsolutePath())
				.show();
		}
	}

	public static void handleEarlyCrash(Throwable e)
	{
		if (!Environment.isCommandLine()) {
			Toolkit.getDefaultToolkit().beep();
			StackTraceDialog.display(e, null);
		}
		System.exit(-1);
	}

	public static void displayStackTrace(Throwable e)
	{
		displayStackTrace(e, null);
	}

	public static void displayStackTrace(Throwable e, File log)
	{
		Logger.printStackTrace(e);

		if (!Environment.isCommandLine()) {
			SwingUtilities.invokeLater(() -> {
				Toolkit.getDefaultToolkit().beep();
				StackTraceDialog.display(e, log);
			});
		}
	}

	public static void openTextFile(File file)
	{
		if (file == null)
			return;

		try {
			Desktop.getDesktop().open(file);
		}
		catch (IOException openDefaultIOE) {
			try {
				if (Environment.isWindows()) {
					Runtime rs = Runtime.getRuntime();
					rs.exec("notepad " + file.getCanonicalPath());
				}
				else {
					openDefaultIOE.printStackTrace();
				}
			}
			catch (IOException nativeIOE) {
				nativeIOE.printStackTrace();
			}
		}
	}

	private static void runCommandLine(String[] args)
	{
		for (int i = 0; i < args.length; i++) {
			switch (args[i].toUpperCase()) {
				case "-VERSION":
					System.out.println("VERSION=" + Environment.getVersionString());
					break;

				case "-COMPILESHAPE":
				case "-COMPILEHIT":
				case "-GENERATESCRIPT":
				case "-COMPILEMAP":
					if (args.length > i + 1) {
						String mapName = args[i + 1];
						Asset mapAsset = AssetManager.getMap(mapName);

						if (mapAsset == null) {
							Logger.logfError("Cannot find map '%s'!", mapName);
							break;
						}

						Map map = Map.loadMap(mapAsset.getFile());
						try {
							if (args[i].equalsIgnoreCase("-CompileMap")) {
								new GeometryCompiler(map);
								new CollisionCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-CompileShape")) {
								new GeometryCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-CompileHit")) {
								new CollisionCompiler(map);
							}
							else if (args[i].equalsIgnoreCase("-GenerateScript")) {
								new ScriptGenerator(map);
							}
							else {
								throw new IllegalStateException();
							}
						}
						catch (BuildException | IOException | InvalidInputException e) {
							Logger.printStackTrace(e);
						}

						i++;
					}
					else
						Logger.logfError("%s expects a mapName argument!", args[i]);
					break;

				case "-COMPILEMAPS":
					try {
						File buildDir = AssetManager.getMapBuildDir();
						for (Asset ah : AssetManager.getMapSources()) {
							// get existing compiled binaries
							String mapName = Map.deriveName(ah.getFile());
							File binShape = new File(buildDir, mapName + "_shape.bin");
							File binHit = new File(buildDir, mapName + "_hit.bin");

							// check if the map source is newer
							boolean buildShape = !binShape.exists() || binShape.lastModified() < ah.lastModified();
							boolean buildHit = !binHit.exists() || binHit.lastModified() < ah.lastModified();

							if (!buildShape && !buildHit) {
								continue;
							}

							try {
								Map map = Map.loadMap(ah.getFile());
								if (buildShape) {
									new GeometryCompiler(map);
								}
								if (buildHit) {
									new CollisionCompiler(map);
								}
							}
							catch (IOException | BuildException e) {
								Logger.printStackTrace(e);
							}
						}
					}
					catch (IOException e) {
						Logger.printStackTrace(e);
					}
					break;

				case "-BUILDPROJECT":
					try {
						Environment.getProject().build();
					}
					catch (Exception e) {
						Logger.logError("Build failed: " + e.getMessage());
					}
					break;

				default:
					Logger.logfError("Unrecognized command line arg: %s", args[i]);
			}
		}
	}

	private static final void trySetIcon(AbstractButton button, ExpectedAsset asset)
	{
		if (!Directories.DUMP.toFile().exists()) {
			Logger.log("Dump directory could not be found.");
			SwingUtils.addBorderPadding(button);
			return;
		}

		ImageIcon imageIcon;

		try {
			imageIcon = new ImageIcon(ImageIO.read(asset.getFile()));
		}
		catch (IOException e) {
			Logger.logError("Exception while reading icon " + asset.getPath());
			SwingUtils.addBorderPadding(button);
			return;
		}

		int size = 24;

		Image image = imageIcon.getImage().getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
		imageIcon = new ImageIcon(image);

		button.setIcon(imageIcon);
		button.setIconTextGap(24);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setVerticalTextPosition(SwingConstants.CENTER);
		button.setHorizontalTextPosition(SwingConstants.RIGHT);
	}

	/**
	 * @return positive = a later than b, negative = b later than a, 0 = equal
	 */
	public static int compareVersionStrings(String a, String b)
	{
		int[] avals, bvals;

		avals = tokenizeVersionString(a);
		bvals = tokenizeVersionString(b);

		for (int i = 0; i < avals.length; i++) {
			if (avals[i] > bvals[i])
				return 1;
			else if (avals[i] < bvals[i])
				return -1;
		}

		return 0;
	}

	private static int[] tokenizeVersionString(String ver)
	{
		if (ver == null || !ver.contains("."))
			throw new IllegalArgumentException("Invalid version string: " + ver);

		String[] tokens = ver.split("\\.");
		int[] values = new int[3];

		for (int i = 0; i < 3; i++) {
			try {
				values[i] = Integer.parseInt(tokens[i]);
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid version string: " + ver);
			}
		}

		return values;
	}
}
