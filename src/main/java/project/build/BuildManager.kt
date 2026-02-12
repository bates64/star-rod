package project.build

import assets.Asset
import kotlinx.coroutines.*
import project.Build
import project.Project
import util.Logger

/**
 * Manages the build lifecycle for a project.
 * Coordinates asset building, file watching, and progress reporting.
 */
class BuildManager(private val project: Project) : Build.ProgressCallback {
	private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(32) + SupervisorJob())
	private var currentBuild: Build? = null
	private var fileWatcher: FileWatcher? = null
	private val listeners = mutableListOf<BuildProgressListener>()

	@Volatile
	var isBuilding: Boolean = false
		private set

	@Volatile
	var buildProgress: BuildProgress = BuildProgress.idle()
		private set

	/**
	 * Starts the build manager.
	 * Begins an initial background build and starts file watching.
	 */
	fun start() {
		Logger.log("Starting build manager for project: ${project.name}")

		// Start initial build
		rebuild(forceRebuild = false)

		// Start file watcher
		fileWatcher = FileWatcher(project) { changedFiles ->
			Logger.log("Detected ${changedFiles.size} file changes, triggering rebuild")
			rebuild(forceRebuild = false)
		}
		fileWatcher?.start()
	}

	/**
	 * Stops the build manager.
	 * Cancels any running builds and stops file watching.
	 */
	fun stop() {
		Logger.log("Stopping build manager")

		// Stop file watcher
		fileWatcher?.stop()
		fileWatcher = null

		// Cancel any running builds
		currentBuild?.cancel()
		currentBuild = null

		// Cancel all coroutines
		scope.cancel()
	}

	/**
	 * Triggers a rebuild.
	 * If a build is already running, this does nothing.
	 */
	fun rebuild(forceRebuild: Boolean) {
		if (isBuilding) {
			Logger.log("Build already in progress, skipping rebuild request")
			return
		}

		scope.launch {
			runBuild(forceRebuild)
		}
	}

	/**
	 * Adds a progress listener.
	 */
	fun addListener(listener: BuildProgressListener) {
		synchronized(listeners) {
			listeners.add(listener)
		}
	}

	/**
	 * Removes a progress listener.
	 */
	fun removeListener(listener: BuildProgressListener) {
		synchronized(listeners) {
			listeners.remove(listener)
		}
	}

	/**
	 * Runs a build asynchronously.
	 */
	private suspend fun runBuild(forceRebuild: Boolean) {
		isBuilding = true

		try {
			val build = Build(project)
			currentBuild = build

			// TODO: Support forceRebuild by deleting build state file

			build.execute(progressCallback = this)

			currentBuild = null
		} catch (e: CancellationException) {
			Logger.log("Build cancelled")
			throw e
		} catch (e: Exception) {
			Logger.logError("Build failed: ${e.message}")
			notifyListeners { onBuildComplete(0, 1) }
		} finally {
			isBuilding = false
		}
	}

	/**
	 * Notifies all listeners with a callback.
	 */
	private fun notifyListeners(callback: BuildProgressListener.() -> Unit) {
		synchronized(listeners) {
			listeners.forEach { it.callback() }
		}
	}

	// Build.ProgressCallback implementation

	override fun onBuildStarted(totalAssets: Int) {
		buildProgress = BuildProgress(
			totalAssets = totalAssets,
			builtAssets = 0,
			failedAssets = 0,
			isComplete = false
		)
		notifyListeners { onBuildStarted(totalAssets) }
	}

	override fun onAssetBuilt(asset: Asset, successCount: Int, totalAssets: Int) {
		buildProgress = buildProgress.copy(
			builtAssets = successCount,
			totalAssets = totalAssets
		)
		notifyListeners { onAssetBuilt(asset, successCount, totalAssets) }
	}

	override fun onAssetFailed(asset: Asset, error: Exception) {
		buildProgress = buildProgress.copy(
			failedAssets = buildProgress.failedAssets + 1
		)
		notifyListeners { onAssetFailed(asset, error) }
	}

	override fun onBuildComplete(successCount: Int, errorCount: Int) {
		buildProgress = buildProgress.copy(
			builtAssets = successCount,
			failedAssets = errorCount,
			isComplete = true
		)
		notifyListeners { onBuildComplete(successCount, errorCount) }
	}
}

/**
 * Represents the current build progress.
 */
data class BuildProgress(
	val totalAssets: Int,
	val builtAssets: Int,
	val failedAssets: Int,
	val isComplete: Boolean
) {
	val percentage: Int
		get() = if (totalAssets > 0) (builtAssets * 100 / totalAssets) else 0

	companion object {
		fun idle(): BuildProgress = BuildProgress(0, 0, 0, true)
	}
}
