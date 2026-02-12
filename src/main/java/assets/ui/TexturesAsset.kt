package assets.ui

import assets.Asset
import assets.AssetsDir
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

/**
 * Textures are directories shaped like this:
 *     name.tex/
 *         textures.json
 *         some_texture.png
 *         another_texture.png
 */
class TexturesAsset(assetsDir: AssetsDir, relativePath: Path) : Asset(assetsDir, relativePath) {
	fun loadTextures(): List<BufferedImage> {
		val textures = mutableListOf<BufferedImage>()
		try {
			val images = mutableListOf<File>()

			Files.newDirectoryStream(path, "*.png").use { stream ->
				for (file in stream) {
					if (Files.isRegularFile(file))
						images.add(file.toFile())
				}
			}

			for (image in images) {
				val img = readImage(image)
				if (img != null)
					textures.add(img)
			}
		} catch (e: IOException) {
			Logger.logError("IOException loading textures from $relativePath: ${e.message}")
		}
		return textures
	}

	@Deprecated("use explorer")
	fun getPreview(index: Int): BufferedImage? = null

	override fun thumbnailHasCheckerboard(): Boolean = false

	override fun loadThumbnail(): Image? {
		val textures = loadTextures()
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
