package assets;

import java.awt.image.BufferedImage;
import java.io.File;

import assets.ui.BackgroundAsset;
import assets.ui.MapAsset;
import assets.ui.TexturesAsset;

public class AssetHandle extends File
{
	public final File assetDir;
	public final String assetPath; // relative path from assetDir

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

	public String getAssetName() {
		int slash = assetPath.lastIndexOf('/');
		if (slash >= 0) {
			return assetPath.substring(slash + 1);
		} else {
			return assetPath;
		}
	}

	public String getAssetDescription() {
		return null;
	}

	/**
	 * Loads a thumbnail image for this asset.
	 * Returns null if no thumbnail is available.
	 */
	public BufferedImage loadThumbnail() {
		return null;
	}

	/**
	 * Upgrades a plain AssetHandle to a typed subclass based on file extension.
	 * Returns null if the file doesn't match any known asset type.
	 */
	public static AssetHandle upgrade(AssetHandle handle)
	{
		String name = handle.getAssetName();
		if (name.endsWith(".xml")) return new MapAsset(handle);
		if (name.endsWith(".png")) return new BackgroundAsset(handle);
		if (name.endsWith(".json")) return new TexturesAsset(handle);
		return null;
	}
}
