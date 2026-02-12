package app

import assets.archive.AssetsArchiveRomPatcher
import project.Build
import project.engine.BuildResult
import util.Logger
import util.ui.ThemedIcon
import java.awt.Color
import java.awt.Dimension
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.io.path.div

/**
 * Button that builds the ROM and launches it in ares emulator.
 * Tracks three states: IDLE, BUILDING, RUNNING.
 */
class PlayButton : JButton() {
	private enum class State {
		IDLE, BUILDING, RUNNING
	}

	private var currentState = State.IDLE
	private var aresProcess: Process? = null

	init {
		icon = ThemedIcon.PLAY_24
		toolTipText = "Run"
		getAccessibleContext().accessibleName = "playButton"
		addActionListener { handleClick() }
		preferredSize = Dimension(48, 48)
		isFocusable = false
	}

	private fun handleClick() {
		when (currentState) {
			State.IDLE -> buildAndLaunch()
			State.BUILDING -> {} // Cancel not implemented yet
			State.RUNNING -> killAndRestart()
		}
	}

	private fun setState(newState: State) {
		currentState = newState
		SwingUtilities.invokeLater {
			when (currentState) {
				State.IDLE -> {
					icon = ThemedIcon.PLAY_24
					isEnabled = true
					background = UIManager.getColor("Button.background")
					isOpaque = false
					isContentAreaFilled = true
					toolTipText = "Run"
				}

				State.BUILDING -> {
					isEnabled = false
					toolTipText = "Building..."
				}

				State.RUNNING -> {
					isEnabled = true
					background = Color(87, 150, 92)
					foreground = Color.WHITE
					isOpaque = true
					isContentAreaFilled = true
					toolTipText = "Restart"
				}
			}
		}
	}

	private fun buildAndLaunch() {
		setState(State.BUILDING)

		// Phase 1: Build engine ROM with output dialog
		Logger.log("Building engine ROM...")
		val dialog = BuildOutputDialog(null)
		dialog.setOnComplete { engineResult ->
			if (!engineResult.isSuccess) {
				setState(State.IDLE)
				return@setOnComplete
			}

			dialog.dispose()

			// Continue with asset build and launch in background
			continueAfterEngineBuild(engineResult)
		}
		dialog.startBuild()
	}

	private fun continueAfterEngineBuild(engineResult: BuildResult) {
		Environment.getExecutor().submit {
			try {
				val project = Environment.getProject()
				val projectDir = project.directory

				// Phase 2: Build project assets into AssetsArchive
				Logger.log("Building project assets...")
				val assetBuild = Build(project)
				val assetsSuccess = assetBuild.executeAsync(generateArchive = true, generateDiorama = false).get()

				if (!assetsSuccess) {
					SwingUtilities.invokeLater {
						setState(State.IDLE)
						JOptionPane.showMessageDialog(
							this,
							"Asset build failed. Check the log for details.",
							"Build Failed",
							JOptionPane.ERROR_MESSAGE
						)
					}
					return@submit
				}

				// Phase 3: Apply AssetsArchive to engine ROM
				Logger.log("Patching ROM with assets...")
				val engineRom = engineResult.outputRom.orElseThrow()
				val projectPath = projectDir.toPath()
				val archivePath = projectPath / ".starrod" / "build" / "assets.bin"
				val patchedRomPath = projectPath / ".starrod" / "build" / "${project.name}.z64"

				// Copy engine ROM to patched location
				Files.copy(
					engineRom.toPath(),
					patchedRomPath,
					StandardCopyOption.REPLACE_EXISTING
				)

				// Apply AssetsArchive patch
				val patcher = AssetsArchiveRomPatcher(patchedRomPath)
				patcher.applyArchive(archivePath, engineResult.outputSyms.orElseThrow())

				// Phase 4: Launch ares
				Logger.log("Launching ares...")
				SwingUtilities.invokeLater { launchAres(patchedRomPath.toFile()) }

			} catch (e: Exception) {
				SwingUtilities.invokeLater {
					setState(State.IDLE)
					StarRodMain.displayStackTrace(e)
				}
			}
		}
	}

	private fun launchAres(rom: File) {
		try {
			val pb = ProcessBuilder("ares", rom.absolutePath)
			pb.directory(Environment.getProjectDirectory())

			aresProcess = pb.start()
			setState(State.RUNNING)

			// Monitor process exit
			Environment.getExecutor().submit {
				try {
					aresProcess!!.waitFor()
					SwingUtilities.invokeLater {
						if (currentState == State.RUNNING) {
							setState(State.IDLE)
							aresProcess = null
						}
					}
				} catch (e: InterruptedException) {
					// Killed intentionally
				}
			}

		} catch (e: IOException) {
			setState(State.IDLE)
			JOptionPane.showMessageDialog(
				this,
				"Could not start ares emulator:\n${e.message}",
				"Failed to Launch Ares",
				JOptionPane.ERROR_MESSAGE
			)
		}
	}

	private fun killAndRestart() {
		aresProcess?.let { process ->
			if (process.isAlive) {
				process.destroyForcibly()
				try {
					process.waitFor(2, TimeUnit.SECONDS)
				} catch (e: InterruptedException) {
					Thread.currentThread().interrupt()
				}
				aresProcess = null
			}
		}
		buildAndLaunch()
	}

	/** Cleanup when application exits. */
	fun cleanup() {
		aresProcess?.let { process ->
			if (process.isAlive) {
				process.destroyForcibly()
			}
		}
	}
}
