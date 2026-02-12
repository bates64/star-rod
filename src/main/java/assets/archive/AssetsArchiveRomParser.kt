package assets.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses AssetsArchive linked list from ROM.
 *
 * The ROM contains a linked list of AssetsArchive nodes starting at a known address.
 * Each node has a sentinel entry with a "next" pointer (0 = end of chain).
 */
class AssetsArchiveRomParser(private val romData: ByteArray) {
	/**
	 * Parses the entire linked list chain starting at the given address.
	 * @param startAddress ROM offset to first node
	 * @return List of nodes in the chain
	 */
	fun parseChain(startAddress: Int): List<AssetsArchiveNode> {
		val nodes = mutableListOf<AssetsArchiveNode>()
		var currentAddress = startAddress

		while (currentAddress != 0 && currentAddress < romData.size) {
			val node = parseNode(currentAddress)
			nodes.add(node)

			if (node.isLastNode)
				break

			currentAddress = node.nextNodeAddress
		}

		return nodes
	}

	/**
	 * Parses a single AssetsArchive node at the given address.
	 */
	private fun parseNode(address: Int): AssetsArchiveNode {
		if (address + 32 > romData.size)
			throw IllegalArgumentException("Invalid node address: $address")

		val buffer = ByteBuffer.wrap(romData, address, romData.size - address)
			.order(ByteOrder.BIG_ENDIAN)

		// Skip header (32 bytes)
		buffer.position(address + 32)

		// Parse TOC entries until we hit the sentinel
		val entries = mutableListOf<AssetsArchiveTocEntry>()
		var nextNodeAddress = 0

		while (true) {
			if (buffer.position() + 76 > romData.size)
				throw IllegalArgumentException("Incomplete TOC entry at ${buffer.position()}")

			// Read name (64 bytes)
			val nameBytes = ByteArray(64)
			buffer.get(nameBytes)
			val name = nameBytes.takeWhile { it != 0.toByte() }
				.toByteArray()
				.toString(Charsets.US_ASCII)

			// Read offset, compressed size, decompressed size (4 bytes each)
			val offset = buffer.int
			val compressedSize = buffer.int
			val decompressedSize = buffer.int

			val entry = AssetsArchiveTocEntry(name, offset, compressedSize, decompressedSize)
			entries.add(entry)

			// Check if this is the sentinel entry
			if (entry.isSentinel) {
				nextNodeAddress = offset  // Sentinel reuses offset field for next pointer
				break
			}
		}

		return AssetsArchiveNode(address, entries, nextNodeAddress)
	}
}
