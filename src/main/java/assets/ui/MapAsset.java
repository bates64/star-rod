package assets.ui;

import static app.Directories.PROJ_THUMBNAIL;

import java.awt.Image;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import app.Environment;
import assets.AssetHandle;
import assets.AssetManager;
import game.map.Map;
import game.map.editor.MapEditor;
import util.Logger;
import util.Priority;

public class MapAsset extends AssetHandle
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();

		long t0 = System.nanoTime();

		AssetManager.getMapSources();

		long t1 = System.nanoTime();
		double sec = (t1 - t0) / 1e9;
		Logger.logf("Loaded map info %.02f ms", sec * 1e3);

		Environment.exit();
	}

	private static final Matcher MapTagMatcher = Pattern.compile("\\s*<Map .+>\\s*").matcher("");
	private static final Matcher MapDescMatcher = Pattern.compile(".+desc=\"([^\"]+)\".+").matcher("");

	public String desc = "";

	public MapAsset(AssetHandle asset)
	{
		super(asset);

		// need to read Map tag quickly, do not parse whole XML file
		try (BufferedReader in = new BufferedReader(new FileReader(asset))) {
			String line;

			while (true) {
				line = in.readLine();
				if (line == null) {
					return; // encountered final line without finding Map tag
				}

				MapTagMatcher.reset(line);

				if (MapTagMatcher.matches())
					break;
			}

			MapDescMatcher.reset(line);
			if (MapDescMatcher.matches()) {
				desc = MapDescMatcher.group(1);
			}
		}
		catch (IOException e) {
			Logger.logError(e.getMessage());
			desc = "READ ERROR";
		}
	}

	@Override
	public String getAssetName() {
		return Map.deriveName(this);
	}

	@Override
	public String getAssetDescription() {
		return desc;
	}

	@Override
	protected Image loadThumbnail()
	{
		File thumbFile = new File(PROJ_THUMBNAIL + assetPath + ".png");
		if (thumbFile.exists()) {
			try {
				return ImageIO.read(thumbFile);
			}
			catch (IOException e) {}
		}

		return null;
	}

	/**
	 * Generates thumbnails for all maps that don't already have one.
	 * Creates a MapEditor instance, so must not be called while one is open.
	 */
	public static void generateMissingThumbnails()
	{
		MapEditor editor = null;

		try {
			for (AssetHandle asset : AssetManager.getMapSources()) {
				File thumbFile = new File(PROJ_THUMBNAIL + asset.assetPath + ".png");
				if (thumbFile.exists())
					continue;
				Logger.log("Capturing thumbnail for " + asset.assetPath + "...", Priority.MILESTONE);
				if (editor == null)
					editor = new MapEditor(false);
				editor.generateThumbnail(asset, thumbFile, THUMBNAIL_SIZE);
			}
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
		}
		finally {
			if (editor != null)
				editor.shutdownThumbnail();
		}
	}
}
