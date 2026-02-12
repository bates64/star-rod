package assets;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import app.Environment;
import app.StarRodMain;
import game.map.Map;
import game.map.compiler.CollisionDecompiler;
import game.map.compiler.GeometryDecompiler;
import game.map.marker.Marker;
import util.Logger;
import util.Priority;

public class AssetExtractor
{
	public static void main(String[] args) throws IOException
	{
		Environment.initialize();
		extractAll();
		Environment.exit();
	}

	private static class MapTemplate
	{
		private final String name;
		private String desc;

		private String shapePath;
		private String hitPath;
		private String texName;
		private String bgName;

		private File shapeFile;
		private File hitFile;

		private boolean hasShapeOverride;
		private boolean hasHitOverride;
		private boolean hasTexOverride;

		private String shapeOverrideName;
		private String hitOverrideName;

		/**
		 * Creates a MapTemplate from CSV definition.
		 * @param mapfsRoot The mapfs root directory
		 * @param mapDef CSV tokens: [mapName, shapePath, hitPath, texPath, bgPath, desc]
		 * @param isStage true if this is a stage, false if map
		 */
		public MapTemplate(File mapfsRoot, String[] mapDef, boolean isStage)
		{
			this.name = mapDef[0];
			this.shapePath = mapDef[1];
			this.hitPath = mapDef[2];
			String texPath = mapDef[3];
			String bgPath = mapDef[4];
			this.desc = mapDef[5];

			String areaName = name.substring(0, 3);
			String extension = isStage ? ".stage" : ".map";

			// Resolve shape file and detect override
			String expectedShapePath = "areas/" + areaName + "/" + name + extension + "/shape.bin";
			hasShapeOverride = !shapePath.equals(expectedShapePath);
			shapeFile = new File(mapfsRoot, shapePath);

			if (hasShapeOverride) {
				// Extract override name from path: "areas/tik/tik_18.stage/shape.bin" -> "tik_18"
				String[] parts = shapePath.split("/");
				if (parts.length >= 3) {
					String dirName = parts[2]; // "tik_18.stage" or "tik_18.map"
					shapeOverrideName = dirName.replaceAll("\\.(map|stage)$", "");
				}
			}

			// Resolve hit file and detect override
			String expectedHitPath = "areas/" + areaName + "/" + name + extension + "/hit.bin";
			hasHitOverride = !hitPath.equals(expectedHitPath);
			hitFile = new File(mapfsRoot, hitPath);

			if (hasHitOverride) {
				String[] parts = hitPath.split("/");
				if (parts.length >= 3) {
					String dirName = parts[2];
					hitOverrideName = dirName.replaceAll("\\.(map|stage)$", "");
				}
			}

			// Resolve texture name
			if (texPath.equals("none")) {
				texName = "";
				hasTexOverride = false;
			}
			else {
				// Extract texture name from path: "areas/kmr/kmr.tex" -> "kmr"
				String[] parts = texPath.split("/");
				if (parts.length >= 2) {
					String texDirName = parts[2]; // "kmr.tex"
					texName = texDirName.replace(".tex", "");
				}
				else {
					texName = "";
				}

				String expectedTexName = areaName;
				hasTexOverride = !texName.equals(expectedTexName);
			}

			// Resolve background name
			if (bgPath.equals("none")) {
				bgName = "";
			}
			else {
				// Extract bg name from path: "backgrounds/kmr.bg.png" -> "kmr"
				String[] parts = bgPath.split("/");
				if (parts.length >= 2) {
					String bgFileName = parts[1]; // "kmr.bg.png"
					bgName = bgFileName.replace(".bg.png", "");
				}
				else {
					bgName = "";
				}
			}
		}

		/**
		 * Returns the directory path for this map (e.g., "areas/kmr/kmr_00.map").
		 */
		public String getMapDirPath()
		{
			// Extract from shapePath: "areas/kmr/kmr_00.map/shape.bin" -> "areas/kmr/kmr_00.map"
			int lastSlash = shapePath.lastIndexOf('/');
			if (lastSlash != -1) {
				return shapePath.substring(0, lastSlash);
			}
			return "";
		}
	}

	public static void extractAll() throws IOException
	{
		// only extract in base asset dir
		File assetDir = AssetManager.getBaseAssetDir();

		File sentinel = new File(assetDir, ".star_rod_extracted");
		if (!sentinel.exists()) {
			Logger.log("Processing engine assets...", Priority.MILESTONE);

			File mapfsDir = AssetSubdir.MAPFS.get(assetDir);

			// extract maps
			for (String mapInfo : app.Resource.getText(app.Resource.ResourceType.Extract, "maps.csv")) {
				String[] tokens = mapInfo.trim().split("\\s*,\\s*");
				MapTemplate template = new MapTemplate(mapfsDir, tokens, false);

				Logger.log("Generating map source: " + template.name);
				Map map = generateMap(template);
				try {
					File outputDir = new File(mapfsDir, template.getMapDirPath());
					File outputFile = new File(outputDir, "map.xml");
					map.saveMapWithoutHeader(outputFile);
				}
				catch (Exception e) {
					StarRodMain.displayStackTrace(e);
				}
			}

			// extract stages
			for (String stageInfo : app.Resource.getText(app.Resource.ResourceType.Extract, "stages.csv")) {
				String[] tokens = stageInfo.trim().split("\\s*,\\s*");
				MapTemplate template = new MapTemplate(mapfsDir, tokens, true);

				Logger.log("Generating stage source: " + template.name);
				Map map = generateMap(template);
				map.isStage = true;
				for (Marker actor : map.getStageMarkers()) {
					map.create(actor);
				}

				try {
					File outputDir = new File(mapfsDir, template.getMapDirPath());
					File outputFile = new File(outputDir, "map.xml");
					map.saveMapWithoutHeader(outputFile);
				}
				catch (Exception e) {
					StarRodMain.displayStackTrace(e);
				}
			}

			FileUtils.touch(sentinel);
		}
	}

	public static Map generateMap(MapTemplate cfg) throws IOException
	{
		Map map = new Map(cfg.name);

		map.hasBackground = !cfg.bgName.isEmpty();
		map.bgName = cfg.bgName;
		map.texName = cfg.texName;

		map.desc = cfg.desc;

		// Set shape override if needed
		if (cfg.hasShapeOverride) {
			map.scripts.overrideShape.set(true);
			map.scripts.shapeOverrideName.set(cfg.shapeOverrideName);
		}

		// Set hit override if needed
		if (cfg.hasHitOverride) {
			map.scripts.overrideHit.set(true);
			map.scripts.hitOverrideName.set(cfg.hitOverrideName);
		}

		// Set texture override if needed
		map.scripts.overrideTex.set(cfg.hasTexOverride);

		// Decompile geometry and collision
		if (cfg.shapeFile != null && cfg.shapeFile.exists()) {
			new GeometryDecompiler(map, cfg.shapeFile);
		}

		if (cfg.hitFile != null && cfg.hitFile.exists()) {
			new CollisionDecompiler(map, cfg.hitFile);
		}

		return map;
	}
}
