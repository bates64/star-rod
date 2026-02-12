package assets.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import util.Logger
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Creates Diorama (TAR.GZ) distribution archives.
 *
 * Archive format:
 * - project.kdl - Project manifest
 * - target.json - Target configuration (engine SHA, ROM addresses)
 * - assets.bin - AssetsArchive binary
 */
class DioramaArchiver(
	private val projectManifest: Path,
	private val config: DioramaConfig,
	private val archiveBin: Path
) {
	/**
	 * Creates a Diorama TAR.GZ archive at the specified path.
	 */
	fun createArchive(outputPath: Path) {
		Logger.log("Creating Diorama archive: ${outputPath.fileName}")

		outputPath.parent?.createDirectories()

		TarArchiveOutputStream(
			GzipCompressorOutputStream(outputPath.outputStream())
		).use { tar ->
			// Set long file name mode for compatibility
			tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)

			// Add project manifest
			addFileEntry(tar, "project.kdl", projectManifest)

			// Add target.json
			addTextEntry(tar, "target.json", config.toJson())

			// Add assets.bin
			addFileEntry(tar, "assets.bin", archiveBin)
		}

		Logger.log("Diorama archive created: ${outputPath.fileSize()} bytes")
	}

	/**
	 * Adds a text entry to the TAR archive.
	 */
	private fun addTextEntry(tar: TarArchiveOutputStream, name: String, content: String) {
		val bytes = content.toByteArray(Charsets.UTF_8)
		val entry = TarArchiveEntry(name)
		entry.size = bytes.size.toLong()
		tar.putArchiveEntry(entry)
		tar.write(bytes)
		tar.closeArchiveEntry()
	}

	/**
	 * Adds a file entry to the TAR archive.
	 */
	private fun addFileEntry(tar: TarArchiveOutputStream, name: String, file: Path) {
		val entry = TarArchiveEntry(file.toFile(), name)
		tar.putArchiveEntry(entry)
		file.inputStream().use { input ->
			input.copyTo(tar)
		}
		tar.closeArchiveEntry()
	}
}
