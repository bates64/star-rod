package project.build

import assets.Asset
import project.Project
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * Immutable context passed to asset build methods.
 */
data class BuildCtx(
	/** Output directory for build artifacts. */
	val buildDir: Path,

	/** Current project being built. */
	val project: Project,

	/** Engine SHA for cache invalidation. */
	val engineSha: String,

	/** Build state format version for migration. */
	val buildStateVersion: Int,

	/** Directory containing generated headers. */
	val headersDir: Path
) {
	/** Returns the path to the typical build artifact for the given asset with an optional suffix. */
	fun artifact(asset: Asset, suffix: String = ""): Path {
		val dir = buildDir / asset.relativePath.parent
		dir.toFile().mkdirs()
		return dir / "${asset.name}$suffix"
	}
}
