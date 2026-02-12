package assets.archive

/** Represents a node in the AssetsArchive linked list in ROM. */
data class AssetsArchiveNode(
	val romAddress: Int,
	val entries: List<AssetsArchiveTocEntry>,
	val nextNodeAddress: Int
) {
	/** Total size of this node in ROM (header + TOC + data). */
	val totalSize: Int
		get() {
			val headerSize = 32
			val tocSize = (entries.size + 1) * 76  // +1 for sentinel
			val dataSize = entries.filter { !it.isSentinel }.sumOf { it.compressedSize }
			return headerSize + tocSize + dataSize
		}

	/** Whether this is the last node in the chain. */
	val isLastNode: Boolean
		get() = nextNodeAddress == 0
}
