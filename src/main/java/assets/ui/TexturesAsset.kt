package assets.ui

import assets.Asset
import assets.AssetSubdir
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import util.Logger
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class TexturesAsset(root: Path, relativePath: Path) : Asset(root, relativePath) {
	private val textures = mutableListOf<BufferedImage>()

	/** Convenience constructor for Java interop. */
	constructor(asset: Asset) : this(asset.root, asset.relativePath)

	init {
		val dirName = "${FilenameUtils.getBaseName(name)}/"
		val dir = File(root.toFile(), "${AssetSubdir.MAP_TEX}$dirName")

		try {
			val images = mutableListOf<File>()

			Files.newDirectoryStream(dir.toPath(), "*.png").use { stream ->
				for (file in stream) {
					if (Files.isRegularFile(file))
						images.add(file.toFile())
				}
			}

			images.shuffle()

			for (image in images) {
				val img = readImage(image)
				if (img != null)
					textures.add(img)
			}
		} catch (e: IOException) {
			Logger.logError("IOException while gathering previews from $dirName")
		}
	}

	fun getPreview(index: Int): BufferedImage? =
		if (index < textures.size) textures[index] else null

	private fun getCompanionDir(): File? {
		val dirName = "${FilenameUtils.getBaseName(name)}/"
		val dir = File(root.toFile(), "${AssetSubdir.MAP_TEX}$dirName")
		return if (dir.isDirectory) dir else null
	}

	override fun delete(): Boolean {
		val dir = getCompanionDir()
		if (dir != null)
			FileUtils.deleteQuietly(dir)
		return super.delete()
	}

	override fun rename(name: String): Boolean {
		val dir = getCompanionDir()
		if (dir != null) {
			try {
				val newDirName = FilenameUtils.getBaseName(name)
				val newDir = File(dir.parentFile, newDirName)
				Files.move(dir.toPath(), newDir.toPath())
			} catch (e: IOException) {
				return false
			}
		}
		return super.rename(name)
	}

	override fun move(targetDir: File): Boolean {
		val dir = getCompanionDir()
		if (dir != null) {
			try {
				FileUtils.moveDirectory(dir, File(targetDir, dir.name))
			} catch (e: IOException) {
				return false
			}
		}
		return super.move(targetDir)
	}

	override fun thumbnailHasCheckerboard(): Boolean = false

	override fun loadThumbnail(): Image? {
		if (textures.isEmpty())
			return null

		val composite = BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB)
		val g = composite.createGraphics()
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)

		var x = 0
		var y = 0
		for (tex in textures) {
			val scale = ROW_HEIGHT.toFloat() / tex.height
			val w = maxOf(1, (tex.width * scale).toInt())
			val h = ROW_HEIGHT

			if (x + w > THUMB_W) {
				// Wrap to next row
				x = 0
				y += ROW_HEIGHT + GAP
				if (y + ROW_HEIGHT > THUMB_H)
					break
			}

			g.drawImage(tex, x, y, w, h, null)
			x += w + GAP
		}

		g.dispose()
		return composite
	}

	companion object {
		private const val THUMB_W = THUMBNAIL_WIDTH
		private const val THUMB_H = THUMBNAIL_HEIGHT
		private const val GAP = 2
		private const val ROW_HEIGHT = (THUMB_H - GAP) / 2 // two rows

		private fun readImage(imgFile: File): BufferedImage? {
			return try {
				ImageIO.read(imgFile)
			} catch (e: IOException) {
				null
			}
		}
	}
}
