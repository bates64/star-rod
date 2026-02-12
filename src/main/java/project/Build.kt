package project

import assets.Asset
import assets.AssetManager
import assets.AssetRegistry
import assets.archive.AssetsArchiveBuilder
import assets.archive.AssetsArchiveCompressor
import assets.archive.DioramaArchiver
import assets.archive.DioramaConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.future
import project.build.*
import util.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

/**
 * Represents a single build operation.
 * Create via Project.createBuild(), execute, then dispose.
 */
class Build(private val project: Project) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	/**
	 * Progress callback for asset building.
	 */
	interface ProgressCallback {
		fun onBuildStarted(totalAssets: Int)
		fun onAssetBuilt(asset: Asset, successCount: Int, totalAssets: Int)
		fun onAssetFailed(asset: Asset, error: Exception)
		fun onBuildComplete(successCount: Int, errorCount: Int)
	}

	/**
	 * Executes the build asynchronously.
	 * @param progressCallback Progress updates during asset building
	 * @param generateArchive Whether to generate AssetsArchive after building
	 * @param generateDiorama Whether to package as Diorama (requires generateArchive=true)
	 * @return true if the build succeeded, false if there were errors
	 */
	suspend fun execute(
		progressCallback: ProgressCallback? = null,
		generateArchive: Boolean = false,
		generateDiorama: Boolean = false
	): Boolean {
		return withContext(Dispatchers.IO) {
			// Phase 1: Build all assets
			val success = buildAllAssets(progressCallback)
			if (!success || !generateArchive) return@withContext success

			// Phase 2: Generate AssetsArchive
			val artifacts = collectArtifacts()
			val archivePath = generateAssetsArchive(artifacts)

			// Phase 3: Package Diorama (optional)
			if (generateDiorama) {
				packageDiorama(archivePath)
			}

			true
		}
	}

	/**
	 * Executes the build asynchronously (Java-friendly version).
	 * @param generateArchive Whether to generate AssetsArchive after building
	 * @param generateDiorama Whether to package as Diorama (requires generateArchive=true)
	 * @return true if the build succeeded, false if there were errors
	 */
	fun executeAsync(
		generateArchive: Boolean = false,
		generateDiorama: Boolean = false
	): CompletableFuture<Boolean> {
		return scope.future {
			execute(
				progressCallback = null,
				generateArchive = generateArchive,
				generateDiorama = generateDiorama
			)
		}
	}

	/**
	 * Cancels the build operation.
	 */
	fun cancel() {
		scope.cancel()
	}

	/**
	 * Discovers all assets in the project by recursively walking the asset directory.
	 * Only scans owned (project) assets, NOT engine assets.
	 *
	 * Note: Engine assets are read-only and never built. If a user modifies an engine asset,
	 * the Asset's Copy-on-Write semantics will automatically copy it to the project directory,
	 * where it will be discovered and built on the next scan.
	 */
	private fun discoverAssets(): List<Asset> {
		// getTopLevelAssetDir() returns the project's owned asset directory (index 0 in the stack)
		// This excludes engine assets which are read-only and at the base of the stack
		val assetRoot = AssetManager.getTopLevelAssetDir().toPath()
		if (!assetRoot.exists() || !assetRoot.isDirectory())
			return emptyList()

		val assets = mutableListOf<Asset>()
		assetRoot.walk()
			.filter { it.isRegularFile() }
			.forEach { file ->
				val relativePath = assetRoot.relativize(file)
				val asset = AssetRegistry.instance.create(relativePath)
				assets.add(asset)
			}

		return assets
	}

	/**
	 * Builds all assets in parallel, with header generation first.
	 * @return true if the build succeeded, false if there were errors
	 */
	private suspend fun buildAllAssets(progressCallback: ProgressCallback?): Boolean {
		val buildDir = project.directory.toPath() / ".starrod" / "build"
		buildDir.createDirectories()

		val headersDir = buildDir / "headers"
		headersDir.createDirectories()

		val stateFile = project.directory.toPath() / ".starrod" / "build-state" / "state.json"
		val engineSha = getEngineSha()

		// Load or create build state
		var buildState = BuildState.load(stateFile, engineSha)
		if (buildState == null) {
			Logger.log("Build state invalidated or missing, rebuilding all assets")
			buildState = BuildState.create(engineSha)
		}

		// Discover all assets
		val allAssets = discoverAssets()
		Logger.log("Discovered ${allAssets.size} assets")

		// Filter to only assets that need rebuilding
		val assetsToRebuild = allAssets.filter { buildState.needsRebuild(it) }
		Logger.log("${assetsToRebuild.size} assets need rebuilding")

		if (assetsToRebuild.isEmpty()) {
			progressCallback?.onBuildComplete(0, 0)
			return true
		}

		progressCallback?.onBuildStarted(assetsToRebuild.size)

		// Generate headers first (in parallel)
		Logger.log("Generating headers...")
		val headerDispatcher = Dispatchers.IO.limitedParallelism(32)
		coroutineScope {
			assetsToRebuild.forEach { asset ->
				launch(headerDispatcher) {
					val headerPath = headersDir / "${asset.name}.hpp"
					try {
						asset.writeHeader(headerPath)
					} catch (e: Exception) {
						Logger.logError("Failed to generate header for ${asset.name}: ${e.message}")
					}
				}
			}
		}

		// Build context for compilation phase
		val ctx = BuildCtx(
			buildDir = buildDir,
			project = project,
			engineSha = engineSha,
			buildStateVersion = BuildState.CURRENT_VERSION,
			headersDir = headersDir
		)

		// Build all assets in parallel
		val (successCount, errorCount, _) = buildAssetsInParallel(
			assetsToRebuild,
			ctx,
			buildState,
			progressCallback,
			0,
			assetsToRebuild.size
		)

		// Save build state (even on partial failure)
		buildState.save(stateFile)

		progressCallback?.onBuildComplete(successCount, errorCount)

		if (errorCount > 0) {
			Logger.logError("Build completed with $errorCount error(s)")
			return false
		} else {
			Logger.log("Build completed successfully: $successCount assets built")
			return true
		}
	}

	/**
	 * Builds a list of assets in parallel with controlled concurrency.
	 * Returns (successCount, errorCount, artifacts).
	 */
	private suspend fun buildAssetsInParallel(
		assets: List<Asset>,
		ctx: BuildCtx,
		buildState: BuildState,
		progressCallback: ProgressCallback?,
		currentSuccessCount: Int,
		totalAssets: Int
	): Triple<Int, Int, List<BuildArtifact>> {
		val dispatcher = Dispatchers.IO.limitedParallelism(32)
		val errorChannel = Channel<Pair<Asset, Exception>>(Channel.UNLIMITED)
		val artifactChannel = Channel<BuildArtifact>(Channel.UNLIMITED)

		var successCount = currentSuccessCount

		// Launch parallel build jobs
		val jobs = assets.map { asset ->
			CoroutineScope(dispatcher + SupervisorJob()).launch {
				try {
					when (val result = asset.build(ctx)) {
						is BuildResult.NoOp -> {
							// Asset doesn't need building, but mark as visited
							buildState.markBuilt(asset)
						}
						is BuildResult.Success -> {
							buildState.markBuilt(asset)
							result.artifacts.forEach { artifactChannel.send(it) }
							synchronized(this@Build) {
								successCount++
								progressCallback?.onAssetBuilt(asset, successCount, totalAssets)
							}
						}
						is BuildResult.Failed -> {
							errorChannel.send(asset to result.error)
							progressCallback?.onAssetFailed(asset, result.error)
						}
					}
				} catch (e: Exception) {
					if (e is CancellationException) throw e
					errorChannel.send(asset to e)
					progressCallback?.onAssetFailed(asset, e)
				}
			}
		}

		// Wait for all jobs to complete
		jobs.forEach { it.join() }

		// Collect errors
		errorChannel.close()
		val errors = mutableListOf<Pair<Asset, Exception>>()
		for (error in errorChannel) {
			errors.add(error)
			Logger.logError("Failed to build ${error.first.relativePath}: ${error.second.message}")
		}

		// Collect artifacts
		artifactChannel.close()
		val artifacts = mutableListOf<BuildArtifact>()
		for (artifact in artifactChannel) {
			artifacts.add(artifact)
		}

		return Triple(successCount - currentSuccessCount, errors.size, artifacts)
	}

	/**
	 * Gets the current engine git SHA for cache invalidation.
	 * Returns "unknown" if not in a git repository.
	 */
	private fun getEngineSha(): String {
		return try {
			val process = ProcessBuilder("git", "rev-parse", "HEAD")
				.directory(project.directory)
				.redirectErrorStream(true)
				.start()

			val sha = process.inputStream.bufferedReader().readText().trim()
			process.waitFor()

			if (process.exitValue() == 0) sha else "unknown"
		} catch (e: Exception) {
			"unknown"
		}
	}

	/**
	 * Collects all built artifacts from the build directory.
	 */
	private fun collectArtifacts(): List<BuildArtifact> {
		val buildDir = project.directory.toPath() / ".starrod" / "build"
		if (!buildDir.exists() || !buildDir.isDirectory())
			return emptyList()

		val artifacts = mutableListOf<BuildArtifact>()

		// Collect binary, shape, and collision artifacts
		buildDir.walk()
			.filter { it.isRegularFile() }
			.filter { !it.startsWith(buildDir / "headers") }  // Exclude headers
			.filter { it.extension in setOf("bin", "shape", "collision") }
			.forEach { file ->
				val type = when (file.extension) {
					"shape" -> ArtifactType.SHAPE
					"collision" -> ArtifactType.COLLISION
					else -> ArtifactType.BINARY
				}
				artifacts.add(BuildArtifact(file, type))
			}

		return artifacts
	}

	/**
	 * Generates an AssetsArchive binary from built artifacts.
	 * @return Path to the generated assets.bin
	 */
	private suspend fun generateAssetsArchive(artifacts: List<BuildArtifact>): Path {
		return withContext(Dispatchers.IO) {
			Logger.log("Generating AssetsArchive from ${artifacts.size} artifacts...")

			val builder = AssetsArchiveBuilder(project.manifest.name)

			// Add each artifact to the archive
			for (artifact in artifacts) {
				val name = artifact.path.fileName.toString()
				val data = artifact.path.readBytes()
				val compress = AssetsArchiveCompressor.shouldCompress(artifact.type)

				builder.addEntry(name, data, compress)
			}

			// Build the archive
			val archiveBin = builder.build()
			val outputPath = project.directory.toPath() / ".starrod" / "build" / "assets.bin"
			outputPath.writeBytes(archiveBin)

			Logger.log("Generated AssetsArchive: ${archiveBin.size} bytes")
			outputPath
		}
	}

	/**
	 * Packages the AssetsArchive into a Diorama distribution file.
	 */
	private suspend fun packageDiorama(archivePath: Path) {
		return withContext(Dispatchers.IO) {
			Logger.log("Packaging Diorama...")

			val projectManifest = project.directory.toPath() / Manifest.FILENAME
			val engineSha = getEngineSha()
			val config = DioramaConfig(
				assetsArchiveRomStart = DioramaConfig.DEFAULT_ROM_START,
				engineSha = engineSha
			)
			val modId = project.manifest.id ?: "unknown"
			val outputPath = project.directory.toPath() / ".starrod" / "build" / "$modId.diorama"

			val archiver = DioramaArchiver(projectManifest, config, archivePath)
			archiver.createArchive(outputPath)

			Logger.log("Diorama engine SHA: $engineSha")
		}
	}
}
