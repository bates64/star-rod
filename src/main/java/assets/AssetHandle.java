package assets;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;

import assets.ui.BackgroundAsset;
import assets.ui.MapAsset;
import assets.ui.TexturesAsset;

public class AssetHandle extends File
{
	public static final int THUMBNAIL_WIDTH = 74;
	public static final int THUMBNAIL_HEIGHT = 60;

	public static final DataFlavor FLAVOUR;
	static {
		try {
			FLAVOUR = new DataFlavor(
				DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + AssetHandle.class.getName() + "\"");
		}
		catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

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
		String name = getName();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	public String getAssetDescription()
	{
		return null;
	}

	/** Deletes this asset from disk. */
	public boolean deleteAsset()
	{
		return FileUtils.deleteQuietly(this);
	}

	/** Renames this asset within its current directory. */
	public boolean renameAsset(String newAssetName)
	{
		// Preserve file extension
		String oldName = getName();
		int dot = oldName.lastIndexOf('.');
		if (dot > 0)
			newAssetName += oldName.substring(dot);

		File newFile = new File(getParentFile(), newAssetName);
		if (newFile.exists())
			return false;
		try {
			Files.move(toPath(), newFile.toPath());
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}

	/** Moves this asset to a different directory. */
	public boolean moveAsset(File targetDir)
	{
		File targetFile = new File(targetDir, getName());
		if (targetFile.exists())
			return false;
		try {
			Files.move(toPath(), targetFile.toPath());
			return true;
		}
		catch (IOException e) {
			return false;
		}
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
		boolean scalingUp = srcW <= maxW && srcH <= maxH;
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
		assert bi.getWidth() <= maxW && bi.getHeight() <= maxH;
		return bi;
	}

	/** Upgrades a plain AssetHandle to a typed subclass based on file extension. */
	public static AssetHandle upgrade(AssetHandle handle)
	{
		String name = handle.getName();
		if (name.endsWith(".xml")) return new MapAsset(handle);
		if (name.endsWith(".png")) return new BackgroundAsset(handle);
		if (name.endsWith(".json")) return new TexturesAsset(handle);
		return null;
	}
}
