package assets.archive

/** Table of Contents entry in an AssetsArchive. */
data class AssetsArchiveTocEntry(
	val name: String,
	val offset: Int,
	val compressedSize: Int,
	val decompressedSize: Int
) {
	val isSentinel: Boolean
		get() = name.startsWith("END DATA")
}
