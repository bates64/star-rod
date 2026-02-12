package assets.archive

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import util.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Applies AssetsArchive patches to ROM files.
 *
 * Supports two patching strategies:
 * 1. Replace existing mod - If the mod is already in the chain and fits
 * 2. Append new node - Extends ROM with new data
 */
class AssetsArchiveRomPatcher(private val romPath: Path) {
	/**
	 * Applies AssetsArchive from a Diorama archive.
	 * Extracts the ROM address from target.json in the Diorama package.
	 * @param dioramaArchive Path to .diorama TAR file
	 */
	fun applyDiorama(dioramaArchive: Path) {
		val archiveBin = extractArchiveFromDiorama(dioramaArchive)
		val romStart = extractRomStartFromDiorama(dioramaArchive)
		try {
			applyArchive(archiveBin, romStart)
		} finally {
			// Clean up temp file
			Files.deleteIfExists(archiveBin)
		}
	}

	/**
	 * Applies AssetsArchive binary directly to ROM.
	 * @param archivePath Path to assets.bin
	 * @param symsFile Linker symbols file containing mapfs_ROM_START
	 * @throws InvalidRomException if ROM is not a valid papermario-dx ROM
	 */
	fun applyArchive(archivePath: Path, symsFile: java.io.File) {
		val romStart = readMapfsRomStart(symsFile)
		Logger.log("Read mapfs_ROM_START = 0x${romStart.toString(16)} from ${symsFile.name}")
		applyArchive(archivePath, romStart)
	}

	/**
	 * Applies AssetsArchive binary directly to ROM.
	 * @param archivePath Path to assets.bin
	 * @param romStart ROM address where AssetsArchive chain begins
	 * @throws InvalidRomException if ROM is not a valid papermario-dx ROM
	 */
	fun applyArchive(archivePath: Path, romStart: Int) {
		Logger.log("Patching ROM...")
		Logger.log("Validating ROM at address 0x${romStart.toString(16)}...")

		val romData = romPath.readBytes()
		val archiveData = archivePath.readBytes()

		// Validate ROM by parsing existing linked list
		val parser = AssetsArchiveRomParser(romData)
		val existingNodes = try {
			val nodes = parser.parseChain(romStart)
			if (nodes.isEmpty()) {
				throw InvalidRomException(
					"ROM does not contain a valid AssetsArchive chain at address 0x${romStart.toString(16)}. " +
					"This ROM is not a compatible papermario-dx build. " +
					"Please ensure you are using a ROM built from papermario-dx, not the original Paper Mario ROM."
				)
			}
			nodes
		} catch (e: InvalidRomException) {
			throw e
		} catch (e: Exception) {
			throw InvalidRomException(
				"Failed to parse AssetsArchive chain at address 0x${romStart.toString(16)}: ${e.message}. " +
				"This ROM is not a compatible papermario-dx build. " +
				"Please ensure you are using a ROM built from papermario-dx, not the original Paper Mario ROM.",
				e
			)
		}

		Logger.log("Found valid AssetsArchive chain with ${existingNodes.size} node(s)")

		// Parse project name from incoming archive
		val incomingProjectName = parseProjectName(archiveData)
		Logger.log("Applying mod: $incomingProjectName")

		// Find if this mod already exists in the chain
		val existingNodeIndex = existingNodes.indexOfFirst { node ->
			val nodeName = parseProjectNameFromRom(romData, node.romAddress)
			nodeName == incomingProjectName
		}

		val patchedRom = if (existingNodeIndex >= 0) {
			val existingNode = existingNodes[existingNodeIndex]
			Logger.log("Mod already exists at 0x${existingNode.romAddress.toString(16)}")

			// Check if new archive fits in existing space
			if (archiveData.size <= existingNode.totalSize) {
				Logger.log("Replacing in-place (fits in existing space)")
				replaceNode(romData, existingNode, archiveData, existingNodes, existingNodeIndex)
			} else {
				Logger.log("New version too large (${archiveData.size} > ${existingNode.totalSize}), appending new node")
				removeNodeAndAppend(romData, existingNodes, existingNodeIndex, archiveData, romStart)
			}
		} else {
			Logger.log("Mod not found in chain, appending new node")
			appendNewNode(romData, existingNodes, archiveData, romStart)
		}

		// Write patched ROM
		romPath.writeBytes(patchedRom)
		Logger.log("ROM patched successfully (${patchedRom.size} bytes)")
	}

	/**
	 * Parses the project name from an AssetsArchive binary.
	 */
	private fun parseProjectName(archiveData: ByteArray): String {
		if (archiveData.size < 32) return "unknown"

		val buffer = ByteBuffer.wrap(archiveData, 6, 16).order(ByteOrder.BIG_ENDIAN)
		val nameBytes = ByteArray(16)
		buffer.get(nameBytes)
		return nameBytes.takeWhile { it != 0.toByte() }
			.toByteArray()
			.toString(Charsets.US_ASCII)
			.trim()
	}

	/**
	 * Parses the project name from an AssetsArchive node in ROM.
	 */
	private fun parseProjectNameFromRom(romData: ByteArray, address: Int): String {
		if (address + 32 > romData.size) return "unknown"

		val buffer = ByteBuffer.wrap(romData, address + 6, 16).order(ByteOrder.BIG_ENDIAN)
		val nameBytes = ByteArray(16)
		buffer.get(nameBytes)
		return nameBytes.takeWhile { it != 0.toByte() }
			.toByteArray()
			.toString(Charsets.US_ASCII)
			.trim()
	}

	/**
	 * Replaces an existing node in-place with new data.
	 */
	private fun replaceNode(
		rom: ByteArray,
		existingNode: AssetsArchiveNode,
		newArchiveData: ByteArray,
		allNodes: List<AssetsArchiveNode>,
		nodeIndex: Int
	): ByteArray {
		val newRom = rom.copyOf()

		// Write new archive data at existing location
		System.arraycopy(newArchiveData, 0, newRom, existingNode.romAddress, newArchiveData.size)

		// Zero out any remaining space
		val remainingSpace = existingNode.totalSize - newArchiveData.size
		if (remainingSpace > 0) {
			val zeroStart = existingNode.romAddress + newArchiveData.size
			for (i in 0 until remainingSpace) {
				newRom[zeroStart + i] = 0
			}
		}

		// If there's a next node, preserve the link
		if (nodeIndex < allNodes.size - 1) {
			val nextNode = allNodes[nodeIndex + 1]
			updateSentinelPointer(newRom, existingNode.romAddress, newArchiveData, nextNode.romAddress)
		}

		return newRom
	}

	/**
	 * Updates the sentinel pointer in an archive to point to the next node.
	 */
	private fun updateSentinelPointer(rom: ByteArray, archiveAddress: Int, archiveData: ByteArray, nextAddress: Int) {
		// Parse the archive to find the sentinel position
		val buffer = ByteBuffer.wrap(archiveData).order(ByteOrder.BIG_ENDIAN)
		buffer.position(32) // Skip header

		// Count TOC entries to find sentinel
		var entryCount = 0
		while (buffer.remaining() >= 76) {
			val nameBytes = ByteArray(64)
			buffer.get(nameBytes)
			val name = nameBytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.US_ASCII)

			if (name.startsWith("END DATA")) {
				// Found sentinel, update its offset field (next 4 bytes)
				val sentinelOffsetPos = archiveAddress + 32 + (entryCount * 76) + 64
				updatePointer(rom, sentinelOffsetPos, nextAddress)
				break
			}

			buffer.position(buffer.position() + 12) // Skip offset, compSize, decompSize
			entryCount++
		}
	}

	/**
	 * Removes a node from the chain and appends a new one.
	 */
	private fun removeNodeAndAppend(
		rom: ByteArray,
		existingNodes: List<AssetsArchiveNode>,
		removeIndex: Int,
		newArchiveData: ByteArray,
		firstNodeAddress: Int
	): ByteArray {
		val nodeToRemove = existingNodes[removeIndex]

		// Update chain to skip removed node
		val newRom = rom.copyOf()

		if (removeIndex == 0 && existingNodes.size == 1) {
			// Only node - will be replaced by append
		} else if (removeIndex == 0) {
			// First node - update firstNodeAddress to point to second node
			val secondNode = existingNodes[1]
			updatePointer(newRom, firstNodeAddress, secondNode.romAddress)
		} else {
			// Middle or last node - update previous node's sentinel
			val prevNode = existingNodes[removeIndex - 1]
			val nextAddress = if (removeIndex < existingNodes.size - 1) {
				existingNodes[removeIndex + 1].romAddress
			} else {
				0 // End of chain
			}

			val headerSize = 32
			val sentinelTocPos = prevNode.romAddress + headerSize + (prevNode.entries.size - 1) * 76
			val nextPointerPos = sentinelTocPos + 64
			updatePointer(newRom, nextPointerPos, nextAddress)
		}

		// Now append the new node
		val remainingNodes = existingNodes.filterIndexed { index, _ -> index != removeIndex }
		return appendNewNode(newRom, if (remainingNodes.isEmpty()) existingNodes.take(1) else remainingNodes, newArchiveData, firstNodeAddress)
	}

	/**
	 * Appends a new AssetsArchive node to the end of ROM.
	 * Updates the linked list to point to the new node.
	 */
	private fun appendNewNode(
		rom: ByteArray,
		existingNodes: List<AssetsArchiveNode>,
		archiveData: ByteArray,
		firstNodeAddress: Int
	): ByteArray {
		require(existingNodes.isNotEmpty()) { "Cannot append to empty chain" }

		// Calculate append address (align to 16-byte boundary for N64)
		val rawAppendAddress = rom.size
		val appendAddress = (rawAppendAddress + 15) and 0xFFFFFFF0.toInt()

		// Create new ROM with archive appended
		val newSize = appendAddress + archiveData.size
		val newRom = ByteArray(newSize)

		// Copy original ROM
		System.arraycopy(rom, 0, newRom, 0, rom.size)

		// Fill alignment padding with zeros
		for (i in rom.size until appendAddress) {
			newRom[i] = 0
		}

		// Append archive data
		System.arraycopy(archiveData, 0, newRom, appendAddress, archiveData.size)

		// Update last node's sentinel to point to new node
		val lastNode = existingNodes.last()
		Logger.log("Linking new node to existing chain (last node at 0x${lastNode.romAddress.toString(16)})")

		// Find sentinel entry position in last node
		val headerSize = 32
		val sentinelTocPos = lastNode.romAddress + headerSize + (lastNode.entries.size - 1) * 76

		// Sentinel's offset field (at +64) contains the next-node pointer
		val nextPointerPos = sentinelTocPos + 64
		updatePointer(newRom, nextPointerPos, appendAddress)

		return newRom
	}

	/**
	 * Updates a 4-byte big-endian pointer in ROM.
	 */
	private fun updatePointer(rom: ByteArray, address: Int, value: Int) {
		val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
		buffer.putInt(value)
		System.arraycopy(buffer.array(), 0, rom, address, 4)
	}

	/**
	 * Extracts assets.bin from a Diorama TAR archive.
	 * @return Path to temporary extracted file
	 */
	private fun extractArchiveFromDiorama(dioramaPath: Path): Path {
		TarArchiveInputStream(
			GzipCompressorInputStream(dioramaPath.inputStream())
		).use { tar ->
			var entry = tar.nextTarEntry
			while (entry != null) {
				if (entry.name == "assets.bin") {
					val tempPath = Files.createTempFile("assets", ".bin")
					tempPath.outputStream().use { output ->
						tar.copyTo(output)
					}
					return tempPath
				}
				entry = tar.nextTarEntry
			}
		}
		throw IllegalArgumentException("Diorama archive missing assets.bin: $dioramaPath")
	}

	/**
	 * Extracts ROM start address from target.json in a Diorama TAR archive.
	 * @return mapfs_ROM_START address
	 */
	private fun extractRomStartFromDiorama(dioramaPath: Path): Int {
		TarArchiveInputStream(
			GzipCompressorInputStream(dioramaPath.inputStream())
		).use { tar ->
			var entry = tar.nextTarEntry
			while (entry != null) {
				if (entry.name == "target.json") {
					val json = tar.readBytes().toString(Charsets.UTF_8)
					val config = kotlinx.serialization.json.Json.decodeFromString<DioramaConfig.TargetJson>(json)
					return config.engine.assets_archive_ROM_START
				}
				entry = tar.nextTarEntry
			}
		}
		throw IllegalArgumentException("Diorama archive missing target.json: $dioramaPath")
	}
}
