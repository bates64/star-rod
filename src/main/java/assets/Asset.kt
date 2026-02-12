package assets

import app.Environment
import org.apache.commons.io.FileUtils
import project.build.BuildCtx
import project.build.BuildResult
import java.awt.Image
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

/**
 * An asset on disk, but not yet loaded because it may be expensive to load.
 * Has Copy-on-Write semantics: if an unowned asset is modified, it is copied to the project so it can be owned.
 */
open class Asset internal constructor(
	var assetsDir: AssetsDir,
	var relativePath: Path,
) {
	init {
		require(relativePath.isAbsolute.not()) {
			"path must be relative: $relativePath"
		}
	}

	/** Root directory containing this asset. */
	val root: Path
		get() = assetsDir.path

	/** Whether this asset is owned by the project. */
	val isOwned: Boolean
		get() = assetsDir.isOwned

	/** Full path on disk. May be a directory. */
	var path: Path
		get() = root / relativePath
		protected set(value) {
			require(value.startsWith(root)) { "path must be within root: $value" }
			relativePath = root.relativize(value)
		}

	val name: String get() = relativePath.nameWithoutAssetExtension
	val extension: String get() = relativePath.assetExtension
	val isDirectory: Boolean get() = path.isDirectory()

	private var cachedThumbnail: Image? = null
	private var thumbnailLoaded = false

	fun getFile(): File = path.toFile()

	fun exists(): Boolean = path.exists()

	open fun getAssetDescription(): String? = null

	/**
	 * Copies this asset to the project directory if it's not already owned.
	 * Updates this asset in-place to point to the project directory.
	 */
	private fun ensureOwned() {
		if (isOwned) return

		// Get the project's owned assets directory
		val project = Environment.getProject()
		val projectAssetsDir = project.ownedAssetsDir
		val projectPath = projectAssetsDir.path / relativePath

		// Copy the file/directory to the project if it doesn't already exist
		if (!projectPath.exists()) {
			if (isDirectory) {
				FileUtils.copyDirectory(path.toFile(), projectPath.toFile())
			} else {
				projectPath.parent?.createDirectories()
				path.copyTo(projectPath, overwrite = false)
			}
		}

		// Update this asset to point to the project directory
		assetsDir = projectAssetsDir
	}

	/** Deletes this asset from disk. Only works for owned assets. */
	open fun delete(): Boolean {
		if (!isOwned) {
			return false
		}
		return FileUtils.deleteQuietly(path.toFile())
	}

	/** Renames this asset within its current directory. CoW: copies to project first if needed. */
	open fun rename(name: String): Boolean {
		ensureOwned()

		// Preserve file extension
		val newPath = path.parent?.resolve("$name.$extension") ?: return false
		if (newPath.exists())
			return false

		return runCatching {
			path = path.moveTo(newPath)
			true
		}.getOrDefault(false)
	}

	// TODO: take a Path
	/** Moves this asset to a different directory. CoW: copies to project first if needed. */
	open fun move(targetDir: File): Boolean {
		ensureOwned()

		val targetPath = targetDir.toPath().resolve(path.name)
		if (targetPath.exists())
			return false

		return runCatching {
			path = path.moveTo(targetPath)
			true
		}.getOrDefault(false)
	}

	/**
	 * Generate a header file for this asset if needed.
	 * Called before build() for all assets.
	 * @param headerPath Path where the header should be written
	 * @return true if a header was generated, false if this asset doesn't need a header
	 */
	open suspend fun writeHeader(headerPath: Path): Boolean = false

	/** Build this asset. Override in subclasses that require compilation. */
	open suspend fun build(ctx: BuildCtx): BuildResult = BuildResult.NoOp

	/** Whether to paint a checkerboard behind the thumbnail for transparency. */
	open fun thumbnailHasCheckerboard(): Boolean = true

	/** Override in subclasses to provide a high-resolution thumbnail image. */
	protected open fun loadThumbnail(): Image? = null

	/** Returns a cached multi-resolution thumbnail, downsized from loadThumbnail. */
	fun getThumbnail(): Image? {
		if (!thumbnailLoaded) {
			thumbnailLoaded = true
			val raw = loadThumbnail()
			if (raw != null)
				cachedThumbnail = createMultiResThumbnail(raw)
		}
		return cachedThumbnail
	}

	override fun toString(): String = "Asset($relativePath)"

	companion object {
		const val THUMBNAIL_WIDTH = 74
		const val THUMBNAIL_HEIGHT = 60

		@JvmField
		val FLAVOUR: DataFlavor = DataFlavor(
			DataFlavor.javaJVMLocalObjectMimeType + ";class=\"${Asset::class.java.name}\""
		)

		private fun createMultiResThumbnail(src: Image): Image {
			val img1x = resizeImage(src, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
			val img2x = resizeImage(src, THUMBNAIL_WIDTH * 2, THUMBNAIL_HEIGHT * 2)
			return BaseMultiResolutionImage(img1x, img2x)
		}

		private fun resizeImage(src: Image, maxW: Int, maxH: Int): BufferedImage {
			val srcW = src.getWidth(null)
			val srcH = src.getHeight(null)

			val scalingUp = srcW <= maxW && srcH <= maxH
			val (dstW, dstH) = if (scalingUp) {
				// Integer scaling with nearest neighbour
				val scale = maxOf(1, minOf(maxW / srcW, maxH / srcH))
				srcW * scale to srcH * scale
			} else {
				// Fit within bounds, preserving aspect ratio
				val ratio = minOf(maxW.toFloat() / srcW, maxH.toFloat() / srcH)
				maxOf(1, (srcW * ratio).toInt()) to maxOf(1, (srcH * ratio).toInt())
			}

			val bi = BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB)
			val g = bi.createGraphics()
			g.setRenderingHint(
				RenderingHints.KEY_INTERPOLATION,
				if (scalingUp) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
				else RenderingHints.VALUE_INTERPOLATION_BILINEAR
			)
			g.drawImage(src, 0, 0, dstW, dstH, null)
			g.dispose()
			assert(bi.width <= maxW && bi.height <= maxH)
			return bi
		}
	}
}
