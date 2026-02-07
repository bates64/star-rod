package project;

import java.io.File;

import app.Environment;
import app.config.Options;

/**
 * Validates whether a directory is a valid Star Rod project.
 * A valid project must have a splat.yaml file in ver/{gameVersion}/.
 */
public class ProjectValidator
{
	private static final String FN_SPLAT = "splat.yaml";

	/**
	 * Checks if a directory is a valid Star Rod project.
	 * @param dir The directory to check
	 * @return true if the directory contains a valid project structure
	 */
	public static boolean isValidProject(File dir)
	{
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return false;
		}

		// Get game version from config (default "us")
		String gameVersion = "us";
		if (Environment.mainConfig != null) {
			String configVersion = Environment.mainConfig.getString(Options.GameVersion);
			if (configVersion != null && !configVersion.isEmpty()) {
				gameVersion = configVersion;
			}
		}

		// Check for splat.yaml in ver/{gameVersion}/
		File versionDir = new File(dir, "ver/" + gameVersion);
		if (!versionDir.exists() || !versionDir.isDirectory()) {
			return false;
		}

		File splatFile = new File(versionDir, FN_SPLAT);
		return splatFile.exists();
	}

	/**
	 * Checks if the current working directory is a valid Star Rod project.
	 * @return true if cwd contains a valid project structure
	 */
	public static boolean isCurrentDirectoryProject()
	{
		return isValidProject(new File("."));
	}
}
