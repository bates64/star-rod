package app.pane;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;
import javax.swing.UIManager;

import util.ui.Squircle;

/**
 * A JPanel with squircle corners.
 */
public class Pane extends JPanel
{
	private final int radius;

	public Pane(int radius)
	{
		this.radius = radius;
		setOpaque(false);
		setBackground(UIManager.getColor("TabbedPane.background"));
	}

	public Pane()
	{
		this(10);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		var shape = Squircle.path(0, 0, getWidth(), getHeight(), radius);

		g2.setColor(getBackground());
		g2.fill(shape);
		g2.dispose();
	}

	@Override
	protected void paintChildren(Graphics g)
	{
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		g2.setClip(Squircle.path(0, 0, getWidth(), getHeight(), radius));
		super.paintChildren(g2);
		g2.dispose();
	}
}
