package app.pane;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import app.Environment;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetManager.DirectoryListing;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ThemedIcon;
import util.ui.UniformGridLayout;

public class AssetsPanel extends JPanel
{
	private String currentPath = "";
	private String selectedName;
	private JPanel selectedPanel;

	private JPanel breadcrumbBar;
	private JPanel resultsPanel;
	private JScrollPane scrollPane;

	private WatchService watchService;
	private Thread watchThread;
	private List<WatchKey> watchKeys = new ArrayList<>();

	private static final int ITEM_SIZE = AssetHandle.THUMBNAIL_WIDTH + 4 * 2;

	public AssetsPanel()
	{
		setLayout(new MigLayout("ins 4, fill", "[grow]", "[pref!][grow]"));

		breadcrumbBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		add(breadcrumbBar, "growx, wrap");

		resultsPanel = new JPanel(new UniformGridLayout(ITEM_SIZE, ITEM_SIZE, 0, 0));
		resultsPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				clearSelection();
			}
		});
		scrollPane = new JScrollPane(resultsPanel);
		scrollPane.setBorder(null);
		add(scrollPane, "grow, push");

		try {
			watchService = FileSystems.getDefault().newWatchService();
			startWatchThread();
		}
		catch (IOException e) {
			Logger.logError("Failed to create file watch service: " + e.getMessage());
		}

		navigateTo("");
	}

	// --- Navigation ---

	private void navigateTo(String path)
	{
		currentPath = path;
		clearSelection();
		rebuildBreadcrumb();
		refresh();
		registerWatchers();
	}

	private void select(String name, JPanel panel)
	{
		if (selectedPanel != null) {
			selectedPanel.repaint();
		}

		selectedName = name;
		selectedPanel = panel;
		panel.repaint();
		rebuildBreadcrumb();
	}

	private void clearSelection()
	{
		if (selectedPanel != null) {
			selectedPanel.repaint();
		}
		selectedName = null;
		selectedPanel = null;
		rebuildBreadcrumb();
	}

	// --- Breadcrumb ---

	private void rebuildBreadcrumb()
	{
		breadcrumbBar.removeAll();

		// Project name as root
		String projectName = Environment.getProject().getManifest().getName();
		JLabel rootLabel = createClickableLabel(projectName);
		rootLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				navigateTo("");
			}
		});
		breadcrumbBar.add(rootLabel);

		// Path components
		if (!currentPath.isEmpty()) {
			String[] parts = currentPath.split("/");
			StringBuilder pathSoFar = new StringBuilder();
			for (String part : parts) {
				if (part.isEmpty())
					continue;
				pathSoFar.append(part).append("/");

				breadcrumbBar.add(createSeparatorLabel());

				String targetPath = pathSoFar.toString();
				JLabel partLabel = createClickableLabel(part);
				partLabel.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent e)
					{
						navigateTo(targetPath);
					}
				});
				breadcrumbBar.add(partLabel);
			}
		}

		// Selected item name
		if (selectedName != null) {
			breadcrumbBar.add(createSeparatorLabel());

			JLabel fileLabel = new JLabel(selectedName);
			fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD));
			breadcrumbBar.add(fileLabel);
		}

		breadcrumbBar.revalidate();
		breadcrumbBar.repaint();
	}

	private JLabel createClickableLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return label;
	}

	private JLabel createSeparatorLabel()
	{
		JLabel sep = new JLabel(" / ");
		sep.setForeground(UIManager.getColor("Label.disabledForeground"));
		return sep;
	}

	// --- Results ---

	private void refresh()
	{
		resultsPanel.removeAll();

		DirectoryListing listing = AssetManager.listDirectory(currentPath);

		// Subdirectories first
		for (String subdirName : listing.subdirectories()) {
			resultsPanel.add(createSubdirItem(subdirName));
		}

		// Then files
		for (AssetHandle asset : listing.files()) {
			resultsPanel.add(createAssetItem(asset));
		}

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel createSubdirItem(String name)
	{
		JPanel panel = createItem(name, ThemedIcon.FOLDER_OPEN_24, null, () -> {
			navigateTo(currentPath + name + "/");
		});
		return panel;
	}

	private JPanel createAssetItem(AssetHandle asset)
	{
		JPanel panel = createItem(asset.getAssetName(), ThemedIcon.PACKAGE_24, asset, () -> {
			openAsset(asset);
		});

		JLabel icon = (JLabel) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);

		// Load thumbnail asynchronously
		new SwingWorker<Image, Void>() {
			@Override
			protected Image doInBackground()
			{
				return asset.getThumbnail();
			}

			@Override
			protected void done()
			{
				try {
					Image thumb = get();
					if (thumb != null) {
						icon.setIcon(new ImageIcon(thumb));
						panel.revalidate();
						panel.repaint();
					}
				}
				catch (Exception e) {
					// ignore — keep default icon
				}
			}
		}.execute();

		String desc = asset.getAssetDescription();
		if (desc != null && !desc.isEmpty()) {
			panel.setToolTipText(desc);
		}

		return panel;
	}

	private JPanel createItem(String name, Icon defaultIcon, AssetHandle asset, Runnable onDoubleClick)
	{
		JPanel panel = createItemPanel();

		boolean checkerboard = asset != null && asset.thumbnailHasCheckerboard();
		JLabel icon = checkerboard ? new JLabel(defaultIcon) {
			@Override
			protected void paintComponent(Graphics g)
			{
				if (getIcon() != null)
					paintCheckerboard((Graphics2D) g, this);
				super.paintComponent(g);
			}
		} : new JLabel(defaultIcon);
		icon.setHorizontalAlignment(JLabel.CENTER);
		icon.setVerticalAlignment(JLabel.CENTER);

		JLabel label = new JLabel(name);
		label.setHorizontalAlignment(JLabel.CENTER);

		panel.add(icon, BorderLayout.CENTER);
		panel.add(label, BorderLayout.SOUTH);

		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.putClientProperty("hovered", true);
				panel.repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				panel.putClientProperty("hovered", false);
				panel.repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 1) {
					select(name, panel);
				}
				else if (e.getClickCount() == 2) {
					onDoubleClick.run();
				}
			}
		});

		return panel;
	}

	private JPanel createItemPanel()
	{
		JPanel panel = new JPanel(new BorderLayout()) {
			@Override
			protected void paintComponent(Graphics g)
			{
				if (this == selectedPanel) {
					Graphics2D g2 = (Graphics2D) g.create();
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					Color bg = UIManager.getColor("Component.borderColor");
					g2.setColor(bg);
					g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
					g2.dispose();
				}

				super.paintComponent(g);
			}
		};
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		panel.setPreferredSize(new Dimension(ITEM_SIZE, ITEM_SIZE));
		panel.setOpaque(false);
		return panel;
	}

	private static void paintCheckerboard(Graphics2D g2, JLabel label)
	{
		Icon icon = label.getIcon();
		int iconW = icon.getIconWidth();
		int iconH = icon.getIconHeight();
		int x = (label.getWidth() - iconW) / 2;
		int y = (label.getHeight() - iconH) / 2;

		Color panelBg = UIManager.getColor("Panel.background");
		boolean dark = panelBg != null && luminance(panelBg) < 0.5f;
		Color c1 = dark ? new Color(0x3C3C3C) : new Color(0xCCCCCC);
		Color c2 = dark ? new Color(0x2C2C2C) : new Color(0xFFFFFF);
		int cs = 4;
		var tile = new BufferedImage(cs * 2, cs * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D tg = tile.createGraphics();
		tg.setColor(c1);
		tg.fillRect(0, 0, cs, cs);
		tg.fillRect(cs, cs, cs, cs);
		tg.setColor(c2);
		tg.fillRect(cs, 0, cs, cs);
		tg.fillRect(0, cs, cs, cs);
		tg.dispose();
		g2.setPaint(new TexturePaint(tile, new Rectangle(x, y, cs * 2, cs * 2)));
		g2.fillRect(x, y, iconW, iconH);
	}

	private static float luminance(Color c)
	{
		return (0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue()) / 255f;
	}

	private void openAsset(AssetHandle asset)
	{
		// TODO
	}

	// --- File watching ---

	private void registerWatchers()
	{
		if (watchService == null)
			return;

		// Cancel existing keys
		for (WatchKey key : watchKeys) {
			key.cancel();
		}
		watchKeys.clear();

		// Register all stack dirs for current path
		for (File dir : AssetManager.getStackDirsForPath(currentPath)) {
			try {
				WatchKey key = dir.toPath().register(watchService,
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_DELETE,
					StandardWatchEventKinds.ENTRY_MODIFY);
				watchKeys.add(key);
			}
			catch (IOException e) {
				Logger.logError("Failed to watch directory: " + dir);
			}
		}
	}

	private void startWatchThread()
	{
		watchThread = new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					WatchKey key = watchService.take();

					// Drain events
					for (WatchEvent<?> event : key.pollEvents()) {
						// just need to know something changed
					}
					key.reset();

					// Debounce: wait a bit for rapid changes to settle
					Thread.sleep(200);

					// Drain any additional events that arrived during debounce
					WatchKey extra;
					while ((extra = watchService.poll()) != null) {
						extra.pollEvents();
						extra.reset();
					}

					SwingUtilities.invokeLater(this::refresh);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}, "AssetsPanel-FileWatcher");
		watchThread.setDaemon(true);
		watchThread.start();
	}

	public void dispose()
	{
		if (watchThread != null) {
			watchThread.interrupt();
		}
		if (watchService != null) {
			try {
				watchService.close();
			}
			catch (IOException e) {
				// ignore on shutdown
			}
		}
	}
}
