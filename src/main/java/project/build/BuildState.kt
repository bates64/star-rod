package project.build

import assets.Asset
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Tracks which assets have been built and their modification times.
 * Also tracks asset class modification times to invalidate when build logic changes.
 * Persisted to `.starrod/build-state/state.json`.
 */
@Serializable
data class BuildState(
	val engineSha: String,
	val version: Int,
	val assetTimestamps: MutableMap<String, Long> = mutableMapOf(),
	val assetClassTimestamps: MutableMap<String, Long> = mutableMapOf()
) {
	/**
	 * Check if an asset needs rebuilding based on its modification time.
	 * Also checks if the asset's class file has changed (build logic changed).
	 */
	fun needsRebuild(asset: Asset): Boolean {
		// Check if asset class changed (build logic changed)
		val className = asset.javaClass.name
		val lastClassTime = assetClassTimestamps[className]
		val currentClassTime = computeClassModificationTime(asset.javaClass)
		if (lastClassTime == null || currentClassTime > lastClassTime) {
			return true
		}

		// Check if asset file changed
		val relativePath = asset.relativePath.pathString
		val lastBuilt = assetTimestamps[relativePath] ?: return true
		val currentModTime = computeModificationTime(asset)
		return currentModTime > lastBuilt
	}

	/**
	 * Mark an asset as successfully built.
	 * Records both the asset file modification time and the asset class modification time.
	 */
	fun markBuilt(asset: Asset) {
		val relativePath = asset.relativePath.pathString
		assetTimestamps[relativePath] = computeModificationTime(asset)

		val className = asset.javaClass.name
		assetClassTimestamps[className] = computeClassModificationTime(asset.javaClass)
	}

	/**
	 * Compute modification time for an asset.
	 * For directories, recursively finds the maximum modification time of all children.
	 */
	private fun computeModificationTime(asset: Asset): Long {
		val path = asset.path
		if (!path.exists())
			return 0L

		return if (path.isDirectory()) {
			computeDirectoryModTime(path)
		} else {
			path.getLastModifiedTime().toMillis()
		}
	}

	/**
	 * Recursively compute the maximum modification time in a directory tree.
	 */
	private fun computeDirectoryModTime(dir: Path): Long {
		if (!dir.isDirectory())
			return 0L

		return dir.walk()
			.filter { it.isRegularFile() }
			.maxOfOrNull { it.getLastModifiedTime().toMillis() }
			?: 0L
	}

	/**
	 * Compute modification time of a class file.
	 * Returns 0 if the class file cannot be found (development mode, etc).
	 */
	private fun computeClassModificationTime(clazz: Class<*>): Long {
		return try {
			// Get the .class file location
			val classFileName = clazz.simpleName + ".class"
			val resource = clazz.getResource(classFileName) ?: return 0L

			// Convert URL to Path if it's a file:// URL
			val path = when (resource.protocol) {
				"file" -> resource.toURI().toPath()
				"jar" -> {
					// For JAR files, get the JAR file's modification time
					val jarPath = resource.path.substringBefore("!")
					if (jarPath.startsWith("file:")) {
						java.net.URI.create(jarPath).toPath()
					} else null
				}
				else -> null
			}

			path?.getLastModifiedTime()?.toMillis() ?: 0L
		} catch (e: Exception) {
			// If we can't determine class file time, return 0 (never invalidate)
			0L
		}
	}

	/**
	 * Save build state to disk.
	 */
	fun save(stateFile: Path) {
		stateFile.parent?.createDirectories()
		val json = Json { prettyPrint = true }
		stateFile.writeText(json.encodeToString(this))
	}

	companion object {
		const val CURRENT_VERSION = 1

		private val json = Json {
			prettyPrint = true
			ignoreUnknownKeys = true
		}

		/**
		 * Load build state from disk, or create a fresh state if the file doesn't exist.
		 * Returns null if the state is invalid (wrong engine SHA or version).
		 */
		fun load(stateFile: Path, currentEngineSha: String): BuildState? {
			if (!stateFile.exists())
				return null

			return try {
				val state = json.decodeFromString<BuildState>(stateFile.readText())
				// Invalidate if engine changed or version mismatch
				if (state.engineSha != currentEngineSha || state.version != CURRENT_VERSION) {
					null
				} else {
					state
				}
			} catch (e: Exception) {
				// Corrupted or incompatible state file
				null
			}
		}

		/**
		 * Create a fresh build state.
		 */
		fun create(engineSha: String): BuildState {
			return BuildState(engineSha, CURRENT_VERSION)
		}
	}
}
