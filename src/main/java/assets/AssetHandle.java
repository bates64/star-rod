package assets;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.File;

import assets.ui.BackgroundAsset;
import assets.ui.MapAsset;
import assets.ui.TexturesAsset;

public class AssetHandle extends File
{
	public static final int THUMBNAIL_WIDTH = 80;
	public static final int THUMBNAIL_HEIGHT = 50;

	public final File assetDir;
	public final String assetPath; // relative path from assetDir

	private Image cachedThumbnail;
	private boolean thumbnailLoaded;

	public AssetHandle(AssetHandle other)
	{
		this(other.assetDir, other.assetPath);
	}

	public AssetHandle(File assetDir, String path)
	{
		super(assetDir, path);

		this.assetDir = assetDir;
		assetPath = path.replaceAll("\\\\", "/"); // resolve all paths with '/' as separator
	}

	public String getAssetName()
	{
		int slash = assetPath.lastIndexOf('/');
		if (slash >= 0)
			return assetPath.substring(slash + 1);
		else
			return assetPath;
	}

	public String getAssetDescription()
	{
		return null;
	}

	/** Whether to paint a checkerboard behind the thumbnail for transparency. */
	public boolean thumbnailHasCheckerboard()
	{
		return true;
	}

	/** Override in subclasses to provide a high-resolution thumbnail image. */
	protected Image loadThumbnail()
	{
		return null;
	}

	/** Returns a cached multi-resolution thumbnail, downsized from loadThumbnail. */
	public final Image getThumbnail()
	{
		if (!thumbnailLoaded) {
			thumbnailLoaded = true;
			Image raw = loadThumbnail();
			if (raw != null)
				cachedThumbnail = createMultiResThumbnail(raw);
		}
		return cachedThumbnail;
	}

	private static Image createMultiResThumbnail(Image src)
	{
		var img1x = resizeImage(src, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
		var img2x = resizeImage(src, THUMBNAIL_WIDTH * 2, THUMBNAIL_HEIGHT * 2);
		return new BaseMultiResolutionImage(img1x, img2x);
	}

	private static BufferedImage resizeImage(Image src, int maxW, int maxH)
	{
		int srcW = src.getWidth(null);
		int srcH = src.getHeight(null);

		int dstW, dstH;
		boolean scalingUp = maxW > srcW || maxH > srcH;
		if (scalingUp) {
			// Integer scaling with nearest neighbour
			int scale = Math.max(1, Math.min(maxW / srcW, maxH / srcH));
			dstW = srcW * scale;
			dstH = srcH * scale;
		}
		else {
			// Fit within bounds, preserving aspect ratio
			float ratio = Math.min((float) maxW / srcW, (float) maxH / srcH);
			dstW = Math.max(1, Math.round(srcW * ratio));
			dstH = Math.max(1, Math.round(srcH * ratio));
		}

		var bi = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			scalingUp ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
			          : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, dstW, dstH, null);
		g.dispose();
		return bi;
	}

	/** Upgrades a plain AssetHandle to a typed subclass based on file extension. */
	public static AssetHandle upgrade(AssetHandle handle)
	{
		String name = handle.getAssetName();
		if (name.endsWith(".xml")) return new MapAsset(handle);
		if (name.endsWith(".png")) return new BackgroundAsset(handle);
		if (name.endsWith(".json")) return new TexturesAsset(handle);
		return null;
	}
}
