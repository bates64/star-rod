package project.build

import assets.AssetManager
import kotlinx.coroutines.*
import project.Project
import util.Logger
import java.nio.file.*
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

/**
 * Watches the project asset directory for file changes and triggers rebuilds.
 * Uses Java WatchService with recursive directory registration and debouncing.
 */
class FileWatcher(
	private val project: Project,
	private val onChange: (changedFiles: Set<Path>) -> Unit
) {
	private var watchService: WatchService? = null
	private var watchJob: Job? = null
	private val watchKeys = mutableListOf<WatchKey>()

	/**
	 * Starts watching for file changes.
	 */
	fun start() {
		try {
			watchService = FileSystems.getDefault().newWatchService()
			registerDirectories()
			startWatchJob()
			Logger.log("File watcher started")
		} catch (e: Exception) {
			Logger.logError("Failed to start file watcher: ${e.message}")
		}
	}

	/**
	 * Stops watching for file changes.
	 */
	fun stop() {
		Logger.log("Stopping file watcher")

		// Cancel watch job
		watchJob?.cancel()
		watchJob = null

		// Cancel all watch keys
		watchKeys.forEach { it.cancel() }
		watchKeys.clear()

		// Close watch service
		watchService?.close()
		watchService = null
	}

	/**
	 * Registers all directories recursively for watching.
	 * Only watches owned (project) assets, NOT engine assets.
	 *
	 * Note: Engine assets are read-only and changes to them are not watched.
	 * If a user modifies an engine asset through the UI, the Asset's Copy-on-Write
	 * semantics will automatically copy it to the project directory, triggering a
	 * file system event that will be caught by this watcher.
	 */
	private fun registerDirectories() {
		// getTopLevelAssetDir() returns the project's owned asset directory (index 0 in the stack)
		// This excludes engine assets which are read-only and at the base of the stack
		val assetRoot = AssetManager.getTopLevelAssetDir().toPath()
		if (!assetRoot.exists() || !assetRoot.isDirectory())
			return

		// Register root directory
		registerDirectory(assetRoot)

		// Register all subdirectories recursively (within owned assets only)
		assetRoot.walk()
			.filter { it.isDirectory() }
			.forEach { registerDirectory(it) }
	}

	/**
	 * Registers a single directory for watching.
	 */
	private fun registerDirectory(dir: Path) {
		try {
			val key = dir.register(
				watchService,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_MODIFY
			)
			watchKeys.add(key)
		} catch (e: Exception) {
			Logger.logError("Failed to register directory for watching: ${dir}: ${e.message}")
		}
	}

	/**
	 * Starts the coroutine that polls for file changes.
	 */
	private fun startWatchJob() {
		watchJob = CoroutineScope(Dispatchers.IO).launch {
			val service = watchService ?: return@launch

			while (isActive) {
				try {
					// Wait for events
					val key = service.take()
					val watchedDir = key.watchable() as? Path

					// Collect all changed files
					val changedFiles = mutableSetOf<Path>()
					for (event in key.pollEvents()) {
						val context = event.context() as? Path ?: continue

						// Build full path
						val fullPath = watchedDir?.resolve(context)

						// If a directory was created, register it for watching
						if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE &&
							fullPath != null && fullPath.isDirectory()) {
							Logger.log("New directory created, registering for watching: $fullPath")
							registerDirectory(fullPath)
							// Register subdirectories recursively
							fullPath.walk()
								.filter { it.isDirectory() && it != fullPath }
								.forEach { registerDirectory(it) }
						}

						if (fullPath != null) {
							changedFiles.add(fullPath)
						}
					}
					key.reset()

					// Debounce: wait 500ms and collect more events
					delay(500)

					var extraKey: WatchKey?
					while (service.poll().also { extraKey = it } != null) {
						extraKey?.let { k ->
							val extraWatchedDir = k.watchable() as? Path
							for (event in k.pollEvents()) {
								val context = event.context() as? Path ?: continue

								// Build full path
								val fullPath = extraWatchedDir?.resolve(context)

								// If a directory was created, register it for watching
								if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE &&
									fullPath != null && fullPath.isDirectory()) {
									Logger.log("New directory created, registering for watching: $fullPath")
									registerDirectory(fullPath)
									// Register subdirectories recursively
									fullPath.walk()
										.filter { it.isDirectory() && it != fullPath }
										.forEach { registerDirectory(it) }
								}

								if (fullPath != null) {
									changedFiles.add(fullPath)
								}
							}
							k.reset()
						}
					}

					// Trigger callback if we have changes
					if (changedFiles.isNotEmpty()) {
						Logger.log("File watcher detected changes: ${changedFiles.map { it.fileName }}")
						onChange(changedFiles)
					}
				} catch (e: InterruptedException) {
					break
				} catch (e: CancellationException) {
					break
				} catch (e: Exception) {
					if (isActive) {
						Logger.logError("File watcher error: ${e.message}")
					}
				}
			}
		}
	}
}
