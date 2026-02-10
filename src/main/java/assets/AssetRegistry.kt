package assets

import assets.ui.BackgroundAsset
import assets.ui.MapAsset
import assets.ui.TexturesAsset
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Registry for asset types. Maps file extensions to factory functions that create typed Asset instances.
 *
 * Built-in types are registered at startup via init().
 * Addon types can be registered dynamically via register() with addon = true.
 *
 * When an unregistered extension is encountered, create() returns a plain Asset instance.
 */
class AssetRegistry {
	internal data class Registration(
		val extension: String,
		val factory: (Path, Path) -> Asset,
		val addon: Boolean,
	)

	internal val byExtension = mutableMapOf<String, Registration>()

	/**
	 * Registers an asset type for a file extension.
	 * @param extension File extension without leading dot (e.g., "xml", "png")
	 * @param factory Constructor reference that takes (root: Path, relativePath: Path) -> Asset
	 * @param addon Whether this is an addon type (used for hot reload)
	 */
	fun register(extension: String, factory: (Path, Path) -> Asset, addon: Boolean = false) {
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
	 * Creates an Asset for the given path.
	 * Returns a typed subclass if the extension is registered, otherwise a plain Asset.
	 */
	fun create(root: Path, relativePath: Path): Asset {
		val ext = relativePath.extension
		val reg = byExtension[ext]
		return reg?.factory?.invoke(root, relativePath) ?: Asset(root, relativePath)
	}

	/**
	 * Returns all assets of a specific type by scanning asset directories.
	 * @param T The asset type to filter by
	 * @param root Root directory to scan
	 * @return List of assets matching the type
	 */
	inline fun <reified T : Asset> getAllOfType(root: Path): List<T> {
		// TODO: Implement
		return emptyList()
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
			instance.register("xml", ::MapAsset)
			instance.register("png", ::BackgroundAsset)
			instance.register("json", ::TexturesAsset)
		}
	}
}
