package assets.archive

import game.yay0.Yay0Helper
import project.build.ArtifactType
import util.Logger

/** Handles Yay0 compression for AssetsArchive entries. */
object AssetsArchiveCompressor {
	/**
	 * Compresses data using Yay0 encoding.
	 * Falls back to uncompressed if compression fails or data is too small.
	 */
	fun compress(data: ByteArray): ByteArray {
		if (data.size < 64) {
			// Not worth compressing small files
			return data
		}

		return try {
			Yay0Helper.encode(data)
		} catch (e: Exception) {
			Logger.logWarning("Compression failed, storing uncompressed: ${e.message}")
			data
		}
	}

	/**
	 * Determines whether an artifact type should be compressed.
	 */
	fun shouldCompress(type: ArtifactType): Boolean = type in setOf(
		ArtifactType.BINARY,
		ArtifactType.SHAPE,
		ArtifactType.COLLISION
	)
}
