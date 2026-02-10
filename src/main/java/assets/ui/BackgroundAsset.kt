package assets.ui

import assets.Asset
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO

class BackgroundAsset(root: Path, relativePath: Path) : Asset(root, relativePath) {
	@JvmField
	val bimg: BufferedImage?

	/** Convenience constructor for Java interop. */
	constructor(asset: Asset) : this(asset.root, asset.relativePath)

	init {
		bimg = try {
			ImageIO.read(getFile())
		} catch (e: IOException) {
			null
		}
	}

	override fun loadThumbnail(): Image? = bimg
}
