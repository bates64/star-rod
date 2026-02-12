package assets.ui

import assets.Asset
import assets.AssetsDir
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

class BackgroundAsset(assetsDir: AssetsDir, relativePath: Path) : Asset(assetsDir, relativePath) {
	@JvmField
	val bimg: BufferedImage?

	init {
		bimg = try {
			ImageIO.read(getFile())
		} catch (e: IOException) {
			null
		}
	}

	override fun loadThumbnail(): Image? = bimg
}
