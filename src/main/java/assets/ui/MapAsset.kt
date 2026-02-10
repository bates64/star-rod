package assets.ui

import app.Directories.PROJ_THUMBNAIL
import app.Environment
import assets.Asset
import assets.AssetManager
import game.map.editor.MapEditor
import util.Logger
import util.Priority
import java.awt.Image
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern
import javax.imageio.ImageIO

class MapAsset(root: Path, relativePath: Path) : Asset(root, relativePath) {
	@JvmField
	var desc: String = ""

	/** Convenience constructor for Java interop. */
	constructor(asset: Asset) : this(asset.root, asset.relativePath)

	init {
		// Read Map tag quickly without parsing whole XML file
		try {
			BufferedReader(FileReader(getFile())).use { reader ->
				var line: String?
				while (true) {
					line = reader.readLine() ?: break // Encountered final line without finding Map tag

					val mapTagMatcher = MAP_TAG_MATCHER.reset(line)
					if (mapTagMatcher.matches()) {
						val descMatcher = MAP_DESC_MATCHER.reset(line)
						if (descMatcher.matches()) {
							desc = descMatcher.group(1)
						}
						break
					}
				}
			}
		} catch (e: IOException) {
			Logger.logError(e.message)
			desc = "READ ERROR"
		}
	}

	override fun getAssetDescription(): String = desc

	override fun delete(): Boolean {
		val thumbFile = File("$PROJ_THUMBNAIL${relativePath}.png")
		if (thumbFile.exists())
			thumbFile.delete()
		return super.delete()
	}

	override fun rename(name: String): Boolean {
		val oldThumb = File("$PROJ_THUMBNAIL$relativePath.png")
		if (oldThumb.exists()) {
			try {
				val lastSlash = relativePath.toString().lastIndexOf('/') + 1
				val newAssetPath = relativePath.toString().substring(0, lastSlash) + name
				val newThumb = File("$PROJ_THUMBNAIL$newAssetPath.png")
				newThumb.parentFile?.mkdirs()
				Files.move(oldThumb.toPath(), newThumb.toPath())
			} catch (e: IOException) {
				return false
			}
		}
		return super.rename(name)
	}

	override fun thumbnailHasCheckerboard(): Boolean = false

	override fun loadThumbnail(): Image? {
		val thumbFile = File("$PROJ_THUMBNAIL$relativePath.png")
		return if (thumbFile.exists()) {
			try {
				ImageIO.read(thumbFile)
			} catch (e: IOException) {
				null
			}
		} else {
			null
		}
	}

	companion object {
		private val MAP_TAG_MATCHER = Pattern.compile("\\s*<Map .+>\\s*").matcher("")
		private val MAP_DESC_MATCHER = Pattern.compile(".+desc=\"([^\"]+)\".+").matcher("")

		/**
		 * Generates thumbnails for all maps that don't already have one.
		 * Creates a MapEditor instance, so must not be called while one is open.
		 */
		@JvmStatic
		fun generateMissingThumbnails() {
			var editor: MapEditor? = null

			try {
				for (asset in AssetManager.getMapSources()) {
					val thumbFile = File("$PROJ_THUMBNAIL${asset.relativePath}.png")
					if (thumbFile.exists())
						continue
					Logger.log("Capturing thumbnail for $asset...", Priority.MILESTONE)
					if (editor == null)
						editor = MapEditor(false)
					editor.generateThumbnail(
						asset.getFile(),
						thumbFile,
						Asset.THUMBNAIL_WIDTH * 2,
						Asset.THUMBNAIL_HEIGHT * 2
					)
				}
			} catch (e: Exception) {
				Logger.printStackTrace(e)
			} finally {
				editor?.shutdownThumbnail()
			}
		}

		@JvmStatic
		fun main(args: Array<String>) {
			Environment.initialize()

			val t0 = System.nanoTime()

			AssetManager.getMapSources()

			val t1 = System.nanoTime()
			val sec = (t1 - t0) / 1e9
			Logger.logf("Loaded map info %.02f ms", sec * 1e3)

			Environment.exit()
		}
	}
}
