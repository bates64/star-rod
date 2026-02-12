package project.build

import assets.Asset
import util.Logger
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Status bar UI component that shows build progress.
 * Implements BuildProgressListener to receive build events.
 */
class BuildStatusBar : JPanel(), BuildProgressListener {
	private val statusLabel = JLabel("Idle")
	private val errors = mutableListOf<Pair<Asset, Exception>>()

	init {
		layout = FlowLayout(FlowLayout.LEFT, 10, 5)
		add(statusLabel)

		// Make clickable to show errors
		cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
		addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (errors.isNotEmpty()) {
					showErrorDialog()
				}
			}
		})

		updateStatus("Idle", Color.GRAY)
	}

	override fun onBuildStarted(totalAssets: Int) {
		SwingUtilities.invokeLater {
			errors.clear()
			updateStatus("Building... 0/$totalAssets assets (0%)", Color.BLUE)
		}
	}

	override fun onAssetBuilt(asset: Asset, successCount: Int, totalAssets: Int) {
		SwingUtilities.invokeLater {
			val percentage = if (totalAssets > 0) (successCount * 100 / totalAssets) else 0
			updateStatus("Building... $successCount/$totalAssets assets ($percentage%)", Color.BLUE)
		}
	}

	override fun onAssetFailed(asset: Asset, error: Exception) {
		SwingUtilities.invokeLater {
			errors.add(asset to error)
		}
	}

	override fun onBuildComplete(successCount: Int, errorCount: Int) {
		SwingUtilities.invokeLater {
			if (errorCount > 0) {
				updateStatus(
					"Build complete with $errorCount error(s) - Click to view",
					Color.RED
				)
			} else if (successCount > 0) {
				updateStatus("Build complete: $successCount assets built", Color(0, 128, 0))
			} else {
				updateStatus("Build complete: No changes", Color(0, 128, 0))
			}
		}
	}

	private fun updateStatus(text: String, color: Color) {
		statusLabel.text = text
		statusLabel.foreground = color
	}

	private fun showErrorDialog() {
		// For now, just log errors to console
		// TODO: Create a proper error dialog UI
		Logger.logError("Build errors:")
		errors.forEach { (asset, error) ->
			Logger.logError("  ${asset.relativePath}: ${error.message}")
		}
	}
}
