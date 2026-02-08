package assets.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import assets.AssetHandle;

public class BackgroundAsset extends AssetHandle
{
	public BufferedImage bimg;

	public BackgroundAsset(AssetHandle asset)
	{
		super(asset);

		try {
			bimg = ImageIO.read(asset);
		}
		catch (IOException e) {
			bimg = null;
		}
	}

	@Override
	protected Image loadThumbnail()
	{
		return bimg;
	}
}
