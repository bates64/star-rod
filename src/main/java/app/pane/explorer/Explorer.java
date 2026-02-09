package app.pane.explorer;

import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import app.Environment;
import app.SwingUtils;
import assets.AssetHandle;
import assets.AssetManager;
import assets.AssetManager.DirectoryListing;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.UniformGridLayout;

public class Explorer extends JPanel
{
	private String currentPath = "";
	private Item selectedItem;

	private JPanel breadcrumbBar;
	private JPanel resultsPanel;
	private JScrollPane scrollPane;

	private WatchService watchService;
	private Thread watchThread;
	private final List<WatchKey> watchKeys = new ArrayList<>();

	public Explorer()
	{
		setLayout(new MigLayout("ins 4, fill", "[grow]", "[pref!][grow]"));

		breadcrumbBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		add(breadcrumbBar, "growx, wrap");

		resultsPanel = new JPanel(new UniformGridLayout(Item.SIZE, Item.SIZE, 0, 0));
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

	void navigateTo(String path)
	{
		currentPath = path;
		clearSelection();
		rebuildBreadcrumb();
		refresh();
		registerWatchers();
	}

	void select(Item item)
	{
		if (selectedItem != null) {
			selectedItem.cancelRename();
			selectedItem.selected = false;
			selectedItem.repaint();
		}

		selectedItem = item;
		item.selected = true;
		item.repaint();
		rebuildBreadcrumb();
	}

	private void clearSelection()
	{
		if (selectedItem != null) {
			selectedItem.cancelRename();
			selectedItem.selected = false;
			selectedItem.repaint();
		}
		selectedItem = null;
		rebuildBreadcrumb();
	}

	void openAsset(AssetHandle asset)
	{
		// TODO
	}

	// --- Breadcrumb ---

	private void rebuildBreadcrumb()
	{
		breadcrumbBar.removeAll();

		String projectName = Environment.getProject().getManifest().getName();
		breadcrumbBar.add(createBreadcrumbLabel(projectName, ""));

		if (!currentPath.isEmpty()) {
			String[] parts = currentPath.split("/");
			var pathSoFar = new StringBuilder();
			for (String part : parts) {
				if (part.isEmpty())
					continue;
				pathSoFar.append(part).append("/");

				breadcrumbBar.add(createSeparatorLabel());
				breadcrumbBar.add(createBreadcrumbLabel(part, pathSoFar.toString()));
			}
		}

		if (selectedItem != null) {
			breadcrumbBar.add(createSeparatorLabel());

			var fileLabel = new JLabel(selectedItem.name);
			fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD));
			breadcrumbBar.add(fileLabel);
		}

		breadcrumbBar.revalidate();
		breadcrumbBar.repaint();
	}

	private JLabel createBreadcrumbLabel(String text, String targetPath)
	{
		var label = new JLabel(text);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final Font normalFont = label.getFont();

		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				navigateTo(targetPath);
			}
		});

		new DropTarget(label, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent dtde)
			{
				if (dtde.isDataFlavorSupported(AssetHandle.FLAVOUR)) {
					dtde.acceptDrag(DnDConstants.ACTION_MOVE);
					label.setFont(normalFont.deriveFont(Font.BOLD));
				}
				else {
					dtde.rejectDrag();
				}
			}

			@Override
			public void dragExit(DropTargetEvent dte)
			{
				label.setFont(normalFont);
			}

			@Override
			public void drop(DropTargetDropEvent dtde)
			{
				label.setFont(normalFont);
				try {
					dtde.acceptDrop(DnDConstants.ACTION_MOVE);
					var asset = (AssetHandle) dtde.getTransferable().getTransferData(AssetHandle.FLAVOUR);

					if (asset.assetPath.startsWith(targetPath) && !asset.assetPath.substring(targetPath.length()).contains("/")) {
						dtde.dropComplete(false);
						return;
					}

					File targetDir = new File(AssetManager.getTopLevelAssetDir(), targetPath);
					targetDir.mkdirs();
					boolean ok = asset.moveAsset(targetDir);
					dtde.dropComplete(ok);

					if (!ok) {
						SwingUtils.getErrorDialog()
							.setTitle("Move Failed")
							.setMessage("Could not move " + asset.getName() + ".")
							.show();
					}
				}
				catch (Exception ex) {
					dtde.dropComplete(false);
					Logger.logError("Drop failed: " + ex.getMessage());
				}
			}
		});

		return label;
	}

	private JLabel createSeparatorLabel()
	{
		var sep = new JLabel(" / ");
		sep.setForeground(UIManager.getColor("Label.disabledForeground"));
		return sep;
	}

	// --- Results ---

	private void refresh()
	{
		resultsPanel.removeAll();

		DirectoryListing listing = AssetManager.listDirectory(currentPath);

		for (String subdirName : listing.subdirectories())
			resultsPanel.add(new DirectoryItem(this, subdirName, currentPath + subdirName + "/"));

		for (AssetHandle asset : listing.files())
			resultsPanel.add(new AssetItem(this, asset));

		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	// --- File watching ---

	private void registerWatchers()
	{
		if (watchService == null)
			return;

		for (WatchKey key : watchKeys)
			key.cancel();
		watchKeys.clear();

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

					for (WatchEvent<?> event : key.pollEvents()) {
						// just need to know something changed
					}
					key.reset();

					SwingUtilities.invokeLater(this::refresh);

					// Debounce: coalesce further events within 200ms
					Thread.sleep(200);

					WatchKey extra;
					while ((extra = watchService.poll()) != null) {
						extra.pollEvents();
						extra.reset();
						SwingUtilities.invokeLater(this::refresh);
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}, "Explorer-FileWatcher");
		watchThread.setDaemon(true);
		watchThread.start();
	}

	public void dispose()
	{
		if (watchThread != null)
			watchThread.interrupt();
		if (watchService != null) {
			try {
				watchService.close();
			}
			catch (IOException e) {
				// ignore on shutdown
			}
		}
	}

	// --- Drag and drop ---

	static class AssetTransferable implements Transferable
	{
		private final AssetHandle asset;

		AssetTransferable(AssetHandle asset)
		{
			this.asset = asset;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[] { AssetHandle.FLAVOUR };
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return AssetHandle.FLAVOUR.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
				throw new UnsupportedFlavorException(flavor);
			return asset;
		}
	}
}
