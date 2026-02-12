package assets

import project.Project as ProjectClass
import project.engine.Engine
import java.nio.file.Path

/**
 * Represents a directory containing assets, with a reference to its owner (Project or Engine).
 * Assets in a Project directory are owned by the project (modifiable).
 * Assets in an Engine directory are owned by the engine (read-only, CoW on modification).
 */
sealed class AssetsDir(
	val path: Path
) {
	/** Assets directory belonging to a project (owned, modifiable). */
	data class Project(
		val project: ProjectClass,
		private val _path: Path
	) : AssetsDir(_path) {
		override val isOwned: Boolean get() = true
	}

	/** Assets directory belonging to the engine (not owned, read-only). */
	data class Engine(
		val engine: project.engine.Engine,
		private val _path: Path
	) : AssetsDir(_path) {
		override val isOwned: Boolean get() = false
	}

	/** Whether this asset is owned by the project (true) or comes from the engine (false). */
	abstract val isOwned: Boolean
}
