package project.build

import assets.Asset

/**
 * Observer interface for build progress events.
 * Implement this to react to build state changes.
 */
interface BuildProgressListener {
	/** Called when a build starts. */
	fun onBuildStarted(totalAssets: Int)

	/** Called when an asset is successfully built. */
	fun onAssetBuilt(asset: Asset, successCount: Int, totalAssets: Int)

	/** Called when an asset fails to build. */
	fun onAssetFailed(asset: Asset, error: Exception)

	/** Called when the build completes (with or without errors). */
	fun onBuildComplete(successCount: Int, errorCount: Int)
}
