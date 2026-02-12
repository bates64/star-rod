package project.build

import java.nio.file.Path

/**
 * Result of building an asset.
 */
sealed class BuildResult {
	/** Asset doesn't need building. */
	object NoOp : BuildResult()

	/** Asset built successfully. */
	data class Success(
		val artifacts: List<BuildArtifact> = emptyList()
	) : BuildResult()

	/** Asset build failed. */
	data class Failed(val error: Exception) : BuildResult()
}

/**
 * A file produced by building an asset.
 */
data class BuildArtifact(
	val path: Path,
	val type: ArtifactType
)

enum class ArtifactType {
	HEADER,
	OBJECT,
	BINARY,
	SHAPE,
	COLLISION,
	OTHER
}
