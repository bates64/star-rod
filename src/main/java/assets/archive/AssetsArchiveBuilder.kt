package assets.archive

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds an AssetsArchive binary in the Paper Mario mapfs format.
 *
 * Binary format:
 * - Header (32 bytes): Magic "MAPFS " + project name (16 bytes) + reserved (10 bytes)
 * - Table of Contents: 76-byte entries (name + offset + compressedSize + decompressedSize)
 * - Sentinel Entry: "END DATA\0" + next-node offset (0 = end of chain)
 * - Asset Data: Compressed (Yay0) or raw binary data
 */
class AssetsArchiveBuilder(private val projectName: String) {
	private val entries = mutableListOf<AssetsArchiveEntry>()

	/**
	 * Adds an entry to the archive.
	 * @param name Entry name (max 63 chars)
	 * @param data Raw data
	 * @param compress Whether to apply Yay0 compression
	 */
	fun addEntry(name: String, data: ByteArray, compress: Boolean = true) {
		val finalData: ByteArray
		val decompressedSize: Int
		val isCompressed: Boolean

		if (compress) {
			val compressed = AssetsArchiveCompressor.compress(data)
			finalData = compressed
			decompressedSize = data.size
			isCompressed = compressed !== data  // Check if compression actually occurred
		} else {
			finalData = data
			decompressedSize = data.size
			isCompressed = false
		}

		entries.add(
			AssetsArchiveEntry(
				name = name,
				data = finalData,
				compressed = isCompressed,
				decompressedSize = decompressedSize
			)
		)
	}

	/**
	 * Builds the complete binary archive.
	 * @return Binary data in mapfs format
	 */
	fun build(): ByteArray {
		val headerSize = 32
		val entrySize = 76
		val sentinelSize = 76
		val tocSize = (entries.size * entrySize) + sentinelSize

		val dataSize = entries.sumOf { it.compressedSize }
		val totalSize = headerSize + tocSize + dataSize

		val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

		writeHeader(buffer)
		writeToc(buffer)
		writeData(buffer)

		return buffer.array()
	}

	private fun writeHeader(buffer: ByteBuffer) {
		// Magic (6 bytes): "MAPFS "
		buffer.put("MAPFS ".toByteArray(Charsets.US_ASCII))

		// Project name (16 bytes, null-terminated)
		val projectNameBytes = projectName.take(15).toByteArray(Charsets.US_ASCII)
		buffer.put(projectNameBytes)
		buffer.put(ByteArray(16 - projectNameBytes.size))  // Null padding

		// Reserved (10 bytes)
		buffer.put(ByteArray(10))
	}

	private fun writeToc(buffer: ByteBuffer) {
		val headerSize = 32
		val entrySize = 76
		val sentinelSize = 76
		val tocSize = (entries.size * entrySize) + sentinelSize

		var dataOffset = headerSize + tocSize

		// Write entries
		for (entry in entries) {
			// Name (64 bytes, null-terminated)
			val nameBytes = entry.name.toByteArray(Charsets.US_ASCII)
			buffer.put(nameBytes)
			buffer.put(ByteArray(64 - nameBytes.size))  // Null padding

			// Offset (4 bytes)
			buffer.putInt(dataOffset)

			// Compressed size (4 bytes)
			buffer.putInt(entry.compressedSize)

			// Decompressed size (4 bytes)
			buffer.putInt(entry.decompressedSize)

			dataOffset += entry.compressedSize
		}

		// Write sentinel entry
		val sentinelName = "END DATA"
		val sentinelBytes = sentinelName.toByteArray(Charsets.US_ASCII)
		buffer.put(sentinelBytes)
		buffer.put(ByteArray(64 - sentinelBytes.size))  // Null padding

		// Next-node offset (4 bytes): 0 = end of chain
		buffer.putInt(0)

		// Compressed size (4 bytes): 0
		buffer.putInt(0)

		// Decompressed size (4 bytes): 0
		buffer.putInt(0)
	}

	private fun writeData(buffer: ByteBuffer) {
		for (entry in entries) {
			buffer.put(entry.data)
		}
	}
}
