package util.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * A JScrollPane with macOS-style scrollbars that fade in on hover and fade out when not in use.
 */
public class FadingScrollPane extends JScrollPane
{
	private static final float FADE_SPEED = 0.1f; // alpha change per repaint
	private static final int CHECK_INTERVAL = 50; // milliseconds

	private float currentAlpha = 0.0f;
	private boolean lastMouseInside = false;

	public FadingScrollPane(Component view)
	{
		super(view);

		setBorder(null);
		setOpaque(false);
		getViewport().setOpaque(false);
		getViewport().setBackground(new Color(0, 0, 0, 0));

		// Configure scrollbars - make everything transparent
		var vScrollBar = getVerticalScrollBar();
		var hScrollBar = getHorizontalScrollBar();

		vScrollBar.setOpaque(false);
		hScrollBar.setOpaque(false);
		vScrollBar.setBackground(new Color(0, 0, 0, 0));
		hScrollBar.setBackground(new Color(0, 0, 0, 0));
		vScrollBar.setBorder(null);
		hScrollBar.setBorder(null);
		vScrollBar.putClientProperty("JScrollBar.showButtons", false);
		hScrollBar.putClientProperty("JScrollBar.showButtons", false);

		// Set initial style with fully transparent scrollbar
		String initialStyle = "track: #00000000; thumb: #00000000";
		vScrollBar.putClientProperty("FlatLaf.style", initialStyle);
		hScrollBar.putClientProperty("FlatLaf.style", initialStyle);

		putClientProperty("JScrollPane.smoothScrolling", true);

		// Periodically check mouse position and trigger repaints as needed
		new Timer(CHECK_INTERVAL, e -> {
			boolean mouseInside = isMouseInside();
			if (mouseInside != lastMouseInside) {
				lastMouseInside = mouseInside;
				repaint();
			}
		}).start();
	}

	private boolean isMouseInside()
	{
		try {
			Point mousePos = MouseInfo.getPointerInfo().getLocation();
			Point componentPos = getLocationOnScreen();
			Rectangle bounds = new Rectangle(componentPos.x, componentPos.y, getWidth(), getHeight());
			return bounds.contains(mousePos);
		}
		catch (Exception e) {
			return false;
		}
	}

	@Override
	public void paint(Graphics g)
	{
		updateScrollbarColors();
		super.paint(g);
	}

	private void updateScrollbarColors()
	{
		float targetAlpha = isMouseInside() ? 1.0f : 0.0f;

		// Interpolate current alpha toward target
		if (currentAlpha < targetAlpha) {
			currentAlpha = Math.min(targetAlpha, currentAlpha + FADE_SPEED);
		}
		else if (currentAlpha > targetAlpha) {
			currentAlpha = Math.max(targetAlpha, currentAlpha - FADE_SPEED);
		}

		Color baseThumb = UIManager.getColor("ScrollBar.thumb");
		if (baseThumb == null) {
			baseThumb = new Color(128, 128, 128);
		}

		// Apply alpha to thumb
		Color thumbColor = new Color(
			baseThumb.getRed(),
			baseThumb.getGreen(),
			baseThumb.getBlue(),
			(int) (currentAlpha * 255)
		);

		String style = String.format(
			"track: #00000000; thumb: #%02x%02x%02x%02x",
			thumbColor.getRed(), thumbColor.getGreen(), thumbColor.getBlue(), thumbColor.getAlpha()
		);

		getVerticalScrollBar().putClientProperty("FlatLaf.style", style);
		getHorizontalScrollBar().putClientProperty("FlatLaf.style", style);

		// Continue animation if not at target
		if (currentAlpha != targetAlpha) {
			repaint();
		}
	}
}
