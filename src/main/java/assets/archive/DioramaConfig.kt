package assets.archive

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Target configuration for Diorama package. */
data class DioramaConfig(
	val assetsArchiveRomStart: Int,
	val engineSha: String
) {
	companion object {
		/** Standard ROM address for AssetsArchive in papermario-dx builds. */
		const val DEFAULT_ROM_START = 0x1E40000
	}
	/**
	 * Generates target.json content.
	 */
	fun toJson(): String {
		val json = Json { prettyPrint = false }
		val target = TargetJson(
			engine = PapermarioDxConfig(
				sha = engineSha,
				assets_archive_ROM_START = assetsArchiveRomStart
			)
		)
		return json.encodeToString(target)
	}

	@Serializable
	internal data class TargetJson(
		val engine: PapermarioDxConfig
	)

	@Serializable
	internal data class PapermarioDxConfig(
		val sha: String,
		val assets_archive_ROM_START: Int
	)
}
