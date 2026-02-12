package assets.archive

/** Entry in an AssetsArchive. */
data class AssetsArchiveEntry(
	val name: String,
	val data: ByteArray,
	val compressed: Boolean,
	val decompressedSize: Int
) {
	val compressedSize: Int get() = data.size

	init {
		require(name.length in 1..63) { "Entry name must be 1-63 characters" }
		require(name.isNotEmpty()) { "Entry name cannot be empty" }
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as AssetsArchiveEntry

		if (name != other.name) return false
		if (!data.contentEquals(other.data)) return false
		if (compressed != other.compressed) return false
		if (decompressedSize != other.decompressedSize) return false

		return true
	}

	override fun hashCode(): Int {
		var result = name.hashCode()
		result = 31 * result + data.contentHashCode()
		result = 31 * result + compressed.hashCode()
		result = 31 * result + decompressedSize
		return result
	}
}
