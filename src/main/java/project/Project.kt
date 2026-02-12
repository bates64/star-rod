package project

import app.Directories
import assets.AssetsDir
import dev.kdl.parse.KdlParseException
import org.apache.commons.io.FileUtils
import project.engine.BuildException
import project.engine.Engine
import java.io.File
import java.io.IOException
import kotlin.io.path.div

/**
 * A fully-loaded project with an initialized engine.
 * Extends [ProjectListing] so it can be used anywhere a listing is expected.
 */
class Project @Throws(IOException::class, KdlParseException::class) constructor(path: File) : ProjectListing(path) {
	val engine: Engine = try {
		Engine.forProject(this)
	} catch (e: BuildException) {
		throw IOException("Failed to initialize engine: ${e.message}", e)
	}

	/**
	 * Asset directory stack for the project.
	 *
	 * Assets are searched from index 0 to last, with first match winning.
	 * Modifications always happen to the owned directory (index 0) via CoW.
	 */
	val assetDirectories: List<AssetsDir>
		get() = buildList {
			add(AssetsDir.Project(this@Project, directory.toPath() / "assets"))
			// Future: add(AssetsDir.Project(dependency, dependencyPath))
			add(AssetsDir.Engine(engine, Directories.US_MAPFS.toFile().toPath())) // mapfs subdir
		}

	/** The owned (project) assets directory. */
	val ownedAssetsDir: AssetsDir.Project
		get() = assetDirectories[0] as AssetsDir.Project

	/** The engine assets directory. */
	val engineAssetsDir: AssetsDir.Engine
		get() = assetDirectories.last() as AssetsDir.Engine

	fun build() {
		// TODO
	}

	companion object {
		/** Creates a new project from a template. */
		@JvmStatic
		@Throws(IOException::class, KdlParseException::class)
		fun create(path: File, template: String, id: String, name: String): ProjectListing {
			if (!path.exists())
				path.mkdirs()
			if (!path.isDirectory)
				throw IllegalArgumentException("Project path must be a directory: $path")

			// Copy entire template directory here
			val templateDir = Directories.DATABASE_TEMPLATES.file(template)
			if (!templateDir.exists())
				throw IllegalArgumentException("Missing template: ${templateDir.path}")
			FileUtils.copyDirectory(templateDir, path)

			// Substitute placeholders in project.kdl
			val manifestFile = File(path, Manifest.FILENAME)
			if (manifestFile.exists()) {
				var content = FileUtils.readFileToString(manifestFile, "UTF-8")
				content = content.replace("\$PROJECT_ID", "\"$id\"")
				content = content.replace("\$PROJECT_NAME", "\"${name.replace("\"", "\\\\")}\"")
				content = content.replace("\$PROJECT_DESCRIPTION", "\"An amazing mod of Paper Mario\"") // TODO: ui
				FileUtils.writeStringToFile(manifestFile, content, "UTF-8")
			}

			// Create assets directory
			val assetsDir = File(path, "assets")
			if (!assetsDir.exists())
				assetsDir.mkdirs()

			return ProjectListing(path)
		}
	}
}
