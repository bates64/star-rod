package app.pane.explorer;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.Transferable;

import javax.swing.ImageIcon;
import javax.swing.SwingWorker;

import assets.Asset;
import util.ui.ThemedIcon;

class AssetItem extends Item
{
	final Asset asset;
	private final boolean owned;

	AssetItem(Tab explorer, Asset asset)
	{
		super(explorer, asset.getName(), ThemedIcon.PACKAGE_24, false);
		this.asset = asset;
		this.owned = asset.isOwned();

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
	protected void paintComponent(Graphics g)
	{
		if (!owned) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
			super.paintComponent(g2);
			g2.dispose();
		}
		else {
			super.paintComponent(g);
		}
	}

	@Override
	void onDoubleClick()
	{
		explorer.openAsset(asset);
	}

	@Override
	Asset getAsset()
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
