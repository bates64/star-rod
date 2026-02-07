package assets;

import java.io.File;

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
}
