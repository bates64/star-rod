package app.pane.explorer;

import java.awt.Image;
import java.awt.datatransfer.Transferable;

import javax.swing.ImageIcon;
import javax.swing.SwingWorker;

import assets.AssetHandle;
import util.ui.ThemedIcon;

class AssetItem extends Item
{
	final AssetHandle asset;

	AssetItem(Tab explorer, AssetHandle asset)
	{
		super(explorer, asset.getAssetName(), ThemedIcon.PACKAGE_24, false);
		this.asset = asset;

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
						setIcon(new ImageIcon(thumb));
						checkerboard = asset.thumbnailHasCheckerboard();
					}
				}
				catch (Exception e) {
					// ignore — keep default icon
				}
			}
		}.execute();

		String desc = asset.getAssetDescription();
		if (desc != null && !desc.isEmpty())
			setToolTipText(desc);
	}

	@Override
	void onDoubleClick()
	{
		explorer.openAsset(asset);
	}

	@Override
	AssetHandle getAsset()
	{
		return asset;
	}

	@Override
	boolean isDraggable()
	{
		return true;
	}

	@Override
	Transferable createDragTransferable()
	{
		return new Tab.AssetTransferable(asset);
	}
}
