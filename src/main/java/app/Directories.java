package app;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import util.Logger;

public enum Directories
{
	// @formatter:off
	//=======================================================================================
	// Directories not related to any specific project

	DATABASE			(Root.CONFIG,			"/database/"),
	DATABASE_EDITOR		(Root.CONFIG, DATABASE,		"/editor/"),
	DATABASE_THEMES		(Root.CONFIG, DATABASE,		"/themes/"),
	DATABASE_TEMPLATES	(Root.CONFIG, DATABASE,		"/templates/"),

	LOGS				(Root.STATE, 			"/logs/"),
	TEMP				(Root.STATE, 			"/temp/"),
	BASEROM             (Root.STATE, 			"/baserom/"),

	//=======================================================================================
	// Directories contain dumped content needed for Star Rod to function
	// These should all eventually become unnecessary and be removed

	DUMP				(Root.STATE,				"/dump/"),
	DUMP_WORLD			(Root.STATE, DUMP,			"/world/"),

	DUMP_ENTITY 		(Root.STATE, DUMP_WORLD,		"/entity/"),
	DUMP_ENTITY_RAW		(Root.STATE, DUMP_ENTITY,		"/raw/"),
	DUMP_ENTITY_SRC		(Root.STATE, DUMP_ENTITY,		"/src/"),

	DUMP_MSG 			(Root.STATE, DUMP,			"/message/"),
	DUMP_MSG_FONT		(Root.STATE, DUMP_MSG,		"/font/"),
	DUMP_FONT_STD		(Root.STATE, DUMP_MSG_FONT,		"/normal/"),
	DUMP_FONT_STD_PAL	(Root.STATE, DUMP_FONT_STD,			"/palette/"),
	DUMP_FONT_CR1		(Root.STATE, DUMP_MSG_FONT,		"/credits-title/"),
	DUMP_FONT_CR1_PAL	(Root.STATE, DUMP_FONT_CR1,			"/palette/"),
	DUMP_FONT_CR2		(Root.STATE, DUMP_MSG_FONT,		"/credits-name/"),
	DUMP_FONT_CR2_PAL	(Root.STATE, DUMP_FONT_CR2,			"/palette/"),

	//=======================================================================================
	// Directories relative to the current project

	PROJ_STAR_ROD		(Root.PROJECT,					"/.starrod/"),
	PROJ_CFG			(Root.PROJECT, PROJ_STAR_ROD,		"/cfg/"),
	PROJ_THUMBNAIL		(Root.PROJECT, PROJ_STAR_ROD,		"/thumbnail/"),

	//=======================================================================================
	// Directories relative to the current project's engine (papermario-dx)

	ENGINE_SRC			(Root.ENGINE,					"/src/"),
	ENGINE_SRC_WORLD		(Root.ENGINE, ENGINE_SRC,			"/world/"),
	ENGINE_SRC_STAGE		(Root.ENGINE, ENGINE_SRC,			"/battle/common/stage/"),
	ENGINE_INCLUDE		(Root.ENGINE,			 		"/include/"),
	ENGINE_INCLUDE_MAPFS	(Root.ENGINE, ENGINE_INCLUDE,		"/mapfs/"),
	ENGINE_ASSETS_US	(Root.ENGINE, 					"/assets/us/");

	// @formatter:on
	//=======================================================================================

	public static final String FN_BASE_ROM = "baserom.z64";

	public static final String FN_SPRITE_SHADING = "sprite_shading_profiles.json";

	public static final String FN_EDITOR_GUIDES = "EditorGuides.json";

	public static final String FN_MAP_EDITOR_CONFIG = "map_editor.cfg";
	public static final String FN_STRING_EDITOR_CONFIG = "string_editor.cfg";
	public static final String FN_SPRITE_EDITOR_CONFIG = "sprite_editor.cfg";

	// extensions
	public static final String EXT_MSG = ".msg";
	public static final String EXT_NEW_TEX = ".json";
	public static final String EXT_OLD_TEX = ".txa";
	public static final String EXT_MAP = ".xml";
	public static final String EXT_PNG = ".png";
	public static final String MAP_BACKUP_SUFFIX = ".backup";
	public static final String MAP_CRASH_SUFFIX = ".crash";

	public static final String EXT_SPRITE = ".xml";
	public static final String FN_SPRITE_TABLE = "SpriteTable.xml";
	public static final String FN_SPRITESHEET = "SpriteSheet.xml";

	public static final String FN_STRING_CONSTANTS = "StringConstants.xml";

	public static final String FN_WORLD_MAP = "world_map.xml";

	private final Root root;
	private final String path;
	private final boolean optional;

	private Directories(Root root, String path)
	{
		this(root, path, false);
	}

	private Directories(Root root, Directories parent, String path)
	{
		this(root, parent, path, false);
	}

	private Directories(Root root, Directories parent, String path, boolean optional)
	{
		this.root = root;
		String fullPath = parent.path + "/" + path;
		this.path = fullPath.replaceAll("/+", "/");
		this.optional = optional;
	}

	private Directories(Root root, String path, boolean optional)
	{
		this.root = root;
		this.path = path;
		this.optional = optional;
	}

	@Override
	public String toString()
	{
		return getRootPath(root) + path;
	}

	public File toFile()
	{
		return new File(this.toString());
	}

	public File file(String filename)
	{
		return new File(this.toString(), filename);
	}

	private enum Root
	{
		NONE, PROJECT, CONFIG, STATE, ENGINE
	}

	private static String getRootPath(Root root)
	{
		switch (root) {
			case NONE:
				return Environment.getWorkingDirectory().getAbsolutePath();
			case PROJECT:
				return projPath;
			case CONFIG:
				return Environment.getUserConfigDir().getAbsolutePath();
			case STATE:
				return Environment.getUserStateDir().getAbsolutePath();
			case ENGINE:
				return Environment.getProject().getEngine().getDirectory().getAbsolutePath();

		}
		return null;
	}

	private static String projPath = null;

	public static void setProjectDirectory(String path)
	{
		if (path.contains("\\"))
			path = path.replaceAll("\\\\", "/");
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		projPath = path;

		Logger.log("Project directory: " + projPath);
	}

	public static void createDumpDirectories() throws IOException
	{
		for (Directories dir : Directories.values()) {
			if (dir.root == Root.STATE && dir.path.startsWith("/dump/") && !dir.optional)
				FileUtils.forceMkdir(dir.toFile());
		}
	}
}
