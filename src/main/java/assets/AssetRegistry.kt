package assets

import app.Environment
import assets.ui.BackgroundAsset
import assets.ui.MapAsset
import assets.ui.TexturesAsset
import util.Logger
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Gets the asset extension for a path by checking registered extensions.
 * For "foo.bg.png", returns "bg.png" if "bg.png" is registered.
 * For "bar.map", returns "map" if "map" is registered.
 * If no registered extension matches, returns an empty string.
 */
val Path.assetExtension: String
	get() {
		val filename = fileName?.toString() ?: return ""
		val parts = filename.split('.')

		if (parts.size < 2) return "" // No extension

		// Try progressively longer extensions, starting from the longest
		// e.g., for "foo.bg.png" try: "bg.png", then "png"
		for (i in 1 until parts.size) {
			val ext = parts.subList(i, parts.size).joinToString(".")
			if (AssetRegistry.instance.byExtension.containsKey(ext)) {
				return ext
			}
		}

		return ""
	}

/**
 * Gets the filename without the asset extension.
 * For "foo.bg.png", returns "foo".
 * For "bar.map", returns "bar".
 */
val Path.nameWithoutAssetExtension: String
	get() {
		val ext = assetExtension
		return if (ext.isNotEmpty()) {
			val filename = fileName?.toString() ?: ""
			filename.removeSuffix(".$ext")
		} else {
			nameWithoutExtension
		}
	}

/**
 * Registry for asset types. Maps file extensions to factory functions that create typed Asset instances.
 *
 * Built-in types are registered at startup via init().
 * Addon types can be registered dynamically via register() with addon = true.
 *
 * All assets are created in the project's owned asset directory.
 */
class AssetRegistry {
	internal data class Registration(
		val extension: String,
		val factory: (AssetsDir, Path) -> Asset,
		val addon: Boolean,
	)

	internal val byExtension = mutableMapOf<String, Registration>()

	/**
	 * Registers an asset type for a file extension.
	 * @param extension File extension without leading dot (e.g., "xml", "png")
	 * @param factory Constructor reference that takes (AssetsDir, relativePath: Path) -> Asset
	 * @param addon Whether this is an addon type (used for hot reload)
	 */
	fun register(extension: String, factory: (AssetsDir, Path) -> Asset, addon: Boolean = false) {
		byExtension[extension] = Registration(extension, factory, addon)
	}

	/**
	 * Unloads all addon-registered types.
	 * After unloading, those extensions will create plain Asset instances.
	 */
	fun unloadAddon() {
		byExtension.values.removeAll { it.addon }
	}

	/**
	 * Creates an Asset for the given AssetsDir and relative path.
	 * Returns a typed subclass if the extension is registered, otherwise a plain Asset.
	 * Use this for discovering existing assets across the asset stack.
	 */
	fun create(assetsDir: AssetsDir, relativePath: Path): Asset {
		val ext = relativePath.assetExtension
		val reg = byExtension[ext]
		return reg?.factory?.invoke(assetsDir, relativePath) ?: Asset(assetsDir, relativePath)
	}

	/**
	 * Creates an Asset for the given relative path.
	 * Always creates assets in the project's owned asset directory.
	 * Returns a typed subclass if the extension is registered, otherwise a plain Asset.
	 * Use this when creating new assets that should be owned by the project.
	 */
	fun create(relativePath: Path): Asset {
		val assetsDir = Environment.getProject().ownedAssetsDir
		return create(assetsDir, relativePath)
	}

	/**
	 * Recursively collects all assets of a specific type across all asset directories.
	 * Skips searching inside directories that have a registered extension.
	 */
	fun <T : Asset> getAll(clazz: Class<T>): List<T> {
		val results = mutableListOf<T>()

		fun collectRecursive(assetsDir: AssetsDir, directory: Path) {
			directory.listDirectoryEntries().forEach { entry ->
				if (entry.isDirectory()) {
					// Check if this directory has a registered extension
					val ext = entry.assetExtension
					if (ext.isNotEmpty()) {
						// This directory is itself an asset - check if it matches our type
						val asset = create(assetsDir, assetsDir.path.relativize(entry))
						if (clazz.isInstance(asset)) {
							@Suppress("UNCHECKED_CAST")
							results.add(asset as T)
						}
						// Don't recurse into asset directories
					} else {
						// Regular directory - recurse into it
						collectRecursive(assetsDir, entry)
					}
				} else if (entry.isRegularFile()) {
					// Check if this file has a registered extension
					val ext = entry.assetExtension
					if (ext.isNotEmpty()) {
						val asset = create(assetsDir, assetsDir.path.relativize(entry))
						if (clazz.isInstance(asset)) {
							@Suppress("UNCHECKED_CAST")
							results.add(asset as T)
						}
					}
				}
			}
		}

		for (assetsDir in Environment.getProject().assetDirectories) {
			val root = assetsDir.path
			if (!root.exists() || !root.isDirectory()) continue
			collectRecursive(assetsDir, root)
		}
		return results
	}

	companion object {
		/**
		 * Global registry instance.
		 * Call init() at startup to register built-in types.
		 */
		@JvmStatic
		val instance = AssetRegistry()

		/**
		 * Registers built-in asset types.
		 * Should be called once at application startup.
		 */
		@JvmStatic
		fun init() {
			instance.register("map", ::MapAsset)
			instance.register("stage", ::MapAsset)
			instance.register("bg.png", ::BackgroundAsset)
			instance.register("tex", ::TexturesAsset)
		}
	}
}
