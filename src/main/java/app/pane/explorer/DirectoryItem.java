package app.pane.explorer;

import java.io.File;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;

import app.SwingUtils;
import assets.Asset;
import assets.AssetManager;
import util.Logger;
import util.ui.ThemedIcon;

class DirectoryItem extends Item
{
	private final String targetPath;

	DirectoryItem(Tab explorer, String name, String targetPath)
	{
		super(explorer, name, ThemedIcon.FOLDER_FILLED.derive(36, 36), false);
		this.targetPath = targetPath;

		new DropTarget(this, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetDragEvent dtde)
			{
				if (dtde.isDataFlavorSupported(Asset.FLAVOUR)) {
					dtde.acceptDrag(DnDConstants.ACTION_MOVE);
					dropTarget = true;
					repaint();
				}
				else {
					dtde.rejectDrag();
				}
			}

			@Override
			public void dragExit(DropTargetEvent dte)
			{
				dropTarget = false;
				repaint();
			}

			@Override
			public void drop(DropTargetDropEvent dtde)
			{
				dropTarget = false;
				repaint();
				try {
					dtde.acceptDrop(DnDConstants.ACTION_MOVE);
					var asset = (Asset) dtde.getTransferable().getTransferData(Asset.FLAVOUR);

					File targetDir = new File(AssetManager.getTopLevelAssetDir(), targetPath);
					targetDir.mkdirs();
					boolean ok = asset.move(targetDir);
					dtde.dropComplete(ok);

					if (!ok) {
						SwingUtils.getErrorDialog()
							.setTitle("Move Failed")
							.setMessage("Could not move " + asset.getName() + " to " + name + "/.")
							.show();
					}
				}
				catch (Exception ex) {
					dtde.dropComplete(false);
					Logger.logError("Drop failed: " + ex.getMessage());
				}
			}
		});
	}

	@Override
	void onDoubleClick()
	{
		explorer.navigateTo(targetPath);
	}
}
