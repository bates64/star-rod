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
	public static final int THUMBNAIL_SIZE = 80 * 2;

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
		var img1x = resizeImage(src, THUMBNAIL_SIZE / 2);
		var img2x = resizeImage(src, THUMBNAIL_SIZE);
		return new BaseMultiResolutionImage(img1x, img2x);
	}

	private static BufferedImage resizeImage(Image src, int targetSize)
	{
		int srcW = src.getWidth(null);
		int srcH = src.getHeight(null);
		float srcRatio = (float) srcW / srcH;
		int w, h;
		if (srcRatio >= 1f) {
			w = targetSize;
			h = Math.max(1, Math.round(targetSize / srcRatio));
		}
		else {
			h = targetSize;
			w = Math.max(1, Math.round(targetSize * srcRatio));
		}

		var bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = bi.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
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
