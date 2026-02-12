package assets;

import static app.Directories.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import assets.ui.BackgroundAsset;
import assets.ui.MapAsset;
import assets.ui.TexturesAsset;
import org.apache.commons.io.FileUtils;

import app.Environment;
import app.input.IOUtils;
import util.Logger;

public class AssetManager
{
	public static File getTopLevelAssetDir()
	{
		return Environment.getProject().getOwnedAssetsDir().getPath().toFile();
	}

	public static File getBaseAssetDir()
	{
		return Environment.getProject().getEngineAssetsDir().getPath().toFile();
	}

	public static Asset get(AssetSubdir subdir, String path)
	{
		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			Asset ah = AssetRegistry.getInstance().create(assetsDir, java.nio.file.Path.of(subdir + path));

			if (ah.exists())
				return ah;
		}
		return AssetRegistry.getInstance().create(java.nio.file.Path.of(subdir + path));
	}

	public static Asset getTopLevel(Asset source)
	{
		return AssetRegistry.getInstance().create(source.getRelativePath());
	}

	public static Asset getBase(AssetSubdir subdir, String path)
	{
		return AssetRegistry.getInstance().create(Environment.getProject().getEngineAssetsDir(), java.nio.file.Path.of(subdir + path));
	}

	/**
	 * Delete an asset at all levels of the asset stack
	 * @param asset
	 */
	public static void deleteAll(Asset asset)
	{
		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			File f = new File(assetsDir.getPath().toFile(), asset.getRelativePath().toString());
			if (f.exists())
				FileUtils.deleteQuietly(f);
		}
	}

	public static TexturesAsset getTextureArchive(String texName)
	{
		Asset asset = get(AssetSubdir.MAPFS, texName);
		if (!asset.exists()) {
			texName = texName.replace("_tex", "");
			asset = get(AssetSubdir.MAPFS,  "areas/" + texName + "/" + texName + ".tex");
		}
		return (TexturesAsset) asset;
	}

	public static MapAsset getMap(String mapName)
	{
		Asset asset = get(AssetSubdir.MAPFS, mapName);
		if (!asset.exists()) {
			String areaName = asset.getName().substring(0, 3);
			asset = get(AssetSubdir.MAPFS,  "areas/" + areaName + "/" + mapName + ".map");
		}
		return (MapAsset) asset;
	}

	public static File getSaveMapFile(String mapName)
	{
		Asset map = getMap(mapName);
		return map.exists() ? map.getPath().toFile() : null;
	}

	public static File getMapBuildDir()
	{
		// Maps are now in areas subdirectories
		return AssetSubdir.MAPFS.getModDir();
	}

	public static BackgroundAsset getBackground(String bgName)
	{
		Asset asset = get(AssetSubdir.MAPFS, bgName);
		if (!asset.exists()) {
			bgName = bgName.replace("_bg", "");
			asset = get(AssetSubdir.MAPFS, "backgrounds/" + bgName + ".bg" + EXT_PNG);
		}
		return (BackgroundAsset) asset;
	}

	public static Asset getNpcSprite(String spriteName)
	{
		return get(AssetSubdir.NPC_SPRITE, spriteName + "/" + FN_SPRITESHEET);
	}

	public static Map<String, Asset> getNpcSpriteRasters(String spriteName) throws IOException
	{
		return getAssetMap(AssetSubdir.NPC_SPRITE, spriteName + "/rasters/", EXT_PNG);
	}

	public static Map<String, Asset> getNpcSpritePalettes(String spriteName) throws IOException
	{
		return getAssetMap(AssetSubdir.NPC_SPRITE, spriteName + "/palettes/", EXT_PNG);
	}

	public static Asset getPlayerSprite(String spriteName)
	{
		return get(AssetSubdir.PLR_SPRITE, spriteName + EXT_SPRITE);
	}

	public static Map<String, Asset> getPlayerSpriteRasters() throws IOException
	{
		return getAssetMap(AssetSubdir.PLR_SPRITE_IMG, EXT_PNG);
	}

	public static Map<String, Asset> getPlayerSpritePalettes() throws IOException
	{
		return getAssetMap(AssetSubdir.PLR_SPRITE_PAL, EXT_PNG);
	}

	/**
	 * Gets all map sources in the new structure (mapfs/areas/**\/*.map/map.xml).
	 * @return Collection of MapAsset instances
	 */
	public static Collection<assets.ui.MapAsset> getMapSources() throws IOException
	{
		return AssetRegistry.getInstance().getAll(MapAsset.class);
	}

	/**
	 * Gets all background images in the new structure (mapfs/backgrounds/*.bg.png).
	 * @return Collection of background image assets
	 */
	public static Collection<BackgroundAsset> getBackgrounds() throws IOException
	{
		return AssetRegistry.getInstance().getAll(BackgroundAsset.class);
	}

	/**
	 * Gets all texture archives in the new structure (mapfs/areas/**\/*.tex/).
	 * @return Collection of texture directory assets
	 */
	public static Collection<TexturesAsset> getTextureArchives() throws IOException
	{
		return AssetRegistry.getInstance().getAll(TexturesAsset.class);
	}

	public static Collection<Asset> getMessages() throws IOException
	{
		return getAssets(AssetSubdir.MSG, EXT_MSG);
	}

	private static Collection<Asset> getAssets(AssetSubdir dir, String ext)
	{
		return getAssets(dir, "", ext, null);
	}

	private static Collection<Asset> getAssets(AssetSubdir dir, String subdir, String ext)
	{
		return getAssets(dir, subdir, ext, null);
	}

	private static Collection<Asset> getAssets(AssetSubdir dir, String ext, Predicate<Path> shouldAccept)
	{
		return getAssets(dir, "", ext, shouldAccept);
	}

	private static Collection<Asset> getAssets(AssetSubdir dir, String subdir, String ext, Predicate<Path> shouldAccept)
	{
		Map<String, Asset> assetMap = getAssetMap(dir, subdir, ext, shouldAccept);

		// return sorted by filename
		return assetMap.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.collect(Collectors.toList());
	}

	private static Map<String, Asset> getAssetMap(AssetSubdir dir, String ext)
	{
		return getAssetMap(dir, "", ext, null);
	}

	private static Map<String, Asset> getAssetMap(AssetSubdir dir, String subdir, String ext)
	{
		return getAssetMap(dir, subdir, ext, null);
	}

	private static Map<String, Asset> getAssetMap(AssetSubdir dir, String ext, Predicate<Path> shouldAccept)
	{
		return getAssetMap(dir, "", ext, shouldAccept);
	}

	private static Map<String, Asset> getAssetMap(AssetSubdir dir, String subdir, String ext, Predicate<Path> shouldAccept)
	{
		Map<String, Asset> assetMap = new HashMap<>();

		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			Path stackDir = assetsDir.getPath();
			Path assetDir = dir.get(stackDir.toFile()).toPath();

			if (!subdir.isEmpty())
				assetDir = assetDir.resolve(subdir);

			if (!Files.exists(assetDir) || !Files.isDirectory(assetDir)) {
				continue;
			}

			// only single directory depth allowed, we can use DirectoryStream instead of Files.walk
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetDir, "*" + ext)) {
				for (Path file : stream) {
					String filename = file.getFileName().toString();

					if (shouldAccept != null && !shouldAccept.test(file))
						continue;

					String relPath = dir + subdir + filename;
					Asset ah = AssetRegistry.getInstance().create(assetsDir, java.nio.file.Path.of(relPath));

					// only add first occurance down the asset stack traversal
					assetMap.putIfAbsent(filename, ah);
				}
			}
			catch (IOException e) {
				Logger.logError("Failed to read directory: " + assetDir);
			}
		}

		return assetMap;
	}

	// --- Generic directory listing ---

	public record DirectoryListing(List<Asset> files, List<String> subdirectories) {}

	/**
	 * Lists all files and subdirectories at a relative path across the asset stack.
	 * Files are returned as Assets (first occurrence in the stack wins).
	 * Subdirectories are returned as names (union across all stack levels).
	 * @param relativePath Relative path from asset root
	 */
	public static DirectoryListing listDirectory(String relativePath)
	{
		Map<String, Asset> fileMap = new HashMap<>();
		TreeSet<String> subdirSet = new TreeSet<>();

		TreeSet<String> ignoredPaths = new TreeSet<>();
		ignoredPaths.add(project.engine.Engine.PROJECT_ENGINE_PATH);

		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			Path stackDir = assetsDir.getPath();
			Path dir = stackDir.resolve(relativePath);

			if (!Files.exists(dir) || !Files.isDirectory(dir))
				continue;

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
				for (Path entry : stream) {
					String name = entry.getFileName().toString();
					if (name.startsWith("."))
						continue;

					String relPath = relativePath + name;
					if (ignoredPaths.contains(relPath))
						continue;

					if (Files.isDirectory(entry)) {
						// Check if directory has a registered extension (e.g., "foo.map" directory)
						Path dirAsPath = java.nio.file.Path.of(relPath);
						String dirExtension = assets.AssetRegistryKt.getAssetExtension(dirAsPath);
						if (!dirExtension.isEmpty()) {
							// Directory has a registered extension, treat it as an asset
							Asset asset = AssetRegistry.getInstance().create(assetsDir, dirAsPath);
							fileMap.putIfAbsent(name, asset);
						} else {
							// Normal subdirectory
							subdirSet.add(name);
						}
					} else {
						Asset asset = AssetRegistry.getInstance().create(assetsDir, java.nio.file.Path.of(relPath));
						fileMap.putIfAbsent(name, asset);
					}
				}
			}
			catch (IOException e) {
				Logger.logError("Failed to read directory: " + dir);
			}
		}

		List<Asset> files = fileMap.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.collect(Collectors.toList());

		return new DirectoryListing(files, new ArrayList<>(subdirSet));
	}

	/**
	 * Returns the real directories across the asset stack that correspond to a relative path.
	 * Only directories that actually exist are returned.
	 */
	public static List<File> getStackDirsForPath(String relativePath)
	{
		List<File> dirs = new ArrayList<>();
		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			File dir = new File(assetsDir.getPath().toFile(), relativePath);
			if (dir.isDirectory())
				dirs.add(dir);
		}
		return dirs;
	}

	public static Collection<Asset> getIcons() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, Asset> assetMap = new TreeMap<>();

		for (AssetsDir assetsDir : Environment.getProject().getAssetDirectories()) {
			File assetDir = assetsDir.getPath().toFile();
			File iconDir = AssetSubdir.ICON.get(assetDir);
			if (!iconDir.exists())
				continue;

			Path dirPath = iconDir.toPath();

			for (File file : IOUtils.getFilesWithExtension(iconDir, EXT_PNG, true)) {
				Path filePath = file.toPath();
				Path relativePath = dirPath.relativize(filePath);
				String relativeString = relativePath.toString();

				if (relativeString.endsWith(".disabled.png")) {
					continue;
				}

				Asset ah = AssetRegistry.getInstance().create(assetsDir, java.nio.file.Path.of(AssetSubdir.ICON + relativeString));
				if (!assetMap.containsKey(relativeString)) {
					assetMap.put(relativeString, ah);
				}
			}
		}

		return assetMap.values();
	}
}
