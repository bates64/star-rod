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

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.input.IOUtils;
import util.Logger;

public class AssetManager
{
	public static File getTopLevelAssetDir()
	{
		return Environment.assetDirectories.get(0);
	}

	public static File getBaseAssetDir()
	{
		int numDirs = Environment.assetDirectories.size();
		return Environment.assetDirectories.get(numDirs - 1);
	}

	public static Asset get(AssetSubdir subdir, String path)
	{
		for (File assetDir : Environment.assetDirectories) {
			Asset ah = new Asset(assetDir, subdir + path);

			if (ah.exists())
				return ah;
		}
		return new Asset(AssetManager.getTopLevelAssetDir(), subdir + path);
	}

	public static Asset getTopLevel(Asset source)
	{
		return new Asset(getTopLevelAssetDir(), source.getRelativePath().toString());
	}

	public static Asset getBase(AssetSubdir subdir, String path)
	{
		return new Asset(getBaseAssetDir(), subdir + path);
	}

	/**
	 * Delete an asset at all levels of the asset stack
	 * @param asset
	 */
	public static void deleteAll(Asset asset)
	{
		for (File assetDir : Environment.assetDirectories) {
			File f = new File(assetDir, asset.getRelativePath().toString());
			if (f.exists())
				FileUtils.deleteQuietly(f);
		}
	}


	public static Asset getTextureArchive(String texName)
	{
		return get(AssetSubdir.MAP_TEX, texName + EXT_NEW_TEX);
	}

	public static File getTexBuildDir()
	{
		return AssetSubdir.MAP_TEX.getModDir();
	}

	public static Asset getMap(String mapName)
	{
		return get(AssetSubdir.MAP_GEOM, mapName + EXT_MAP);
	}

	public static File getSaveMapFile(String mapName)
	{
		return new File(getMapBuildDir(), mapName + EXT_MAP);
	}

	public static File getMapBuildDir()
	{
		return AssetSubdir.MAP_GEOM.getModDir();
	}

	public static Asset getBackground(String bgName)
	{
		return get(AssetSubdir.MAP_BG, bgName + EXT_PNG);
	}

	public static File getBackgroundBuildDir()
	{
		return AssetSubdir.MAP_BG.getModDir();
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

	public static Collection<Asset> getMapSources() throws IOException
	{
		return getAssets(AssetSubdir.MAP_GEOM, EXT_MAP, (p) -> {
			// skip crash and backup files
			String filename = p.getFileName().toString();
			return !(filename.endsWith(MAP_CRASH_SUFFIX) || filename.endsWith(MAP_BACKUP_SUFFIX));
		});
	}

	public static Collection<Asset> getBackgrounds() throws IOException
	{
		return getAssets(AssetSubdir.MAP_BG, EXT_PNG);
	}

	public static Collection<Asset> getLegacyTextureArchives() throws IOException
	{
		return getAssets(AssetSubdir.MAP_TEX, EXT_OLD_TEX);
	}

	public static Collection<Asset> getTextureArchives() throws IOException
	{
		return getAssets(AssetSubdir.MAP_TEX, EXT_NEW_TEX);
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

		for (File stackDir : Environment.assetDirectories) {
			Path assetDir = dir.get(stackDir).toPath();

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
					Asset ah = new Asset(stackDir, relPath);

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
	 * @param relativePath Relative path from asset root, e.g. "" for root, "mapfs/", "mapfs/geom/"
	 */
	public static DirectoryListing listDirectory(String relativePath)
	{
		Map<String, Asset> fileMap = new HashMap<>();
		TreeSet<String> subdirSet = new TreeSet<>();

		TreeSet<String> ignoredPaths = new TreeSet<>();
		ignoredPaths.add(project.engine.Engine.PROJECT_ENGINE_PATH);

		for (File stackDir : Environment.assetDirectories) {
			Path dir = stackDir.toPath().resolve(relativePath);

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
						subdirSet.add(name);
					} else {
						Asset asset = AssetRegistry.getInstance().create(stackDir.toPath(), java.nio.file.Path.of(relPath));
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
		for (File stackDir : Environment.assetDirectories) {
			File dir = new File(stackDir, relativePath);
			if (dir.isDirectory())
				dirs.add(dir);
		}
		return dirs;
	}

	public static Collection<Asset> getIcons() throws IOException
	{
		// use TreeMap to keep assets sorted
		TreeMap<String, Asset> assetMap = new TreeMap<>();

		for (File assetDir : Environment.assetDirectories) {
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

				Asset ah = new Asset(assetDir, AssetSubdir.ICON + relativeString);
				if (!assetMap.containsKey(relativeString)) {
					assetMap.put(relativeString, ah);
				}
			}
		}

		return assetMap.values();
	}
}
