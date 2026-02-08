package util.ui;

import java.awt.*;
import javax.swing.*;

/**
 * A layout manager that arranges uniform-size cells in a responsive grid.
 * The number of columns adjusts automatically based on the container width.
 */
public class UniformGridLayout implements LayoutManager
{
	private final int cellWidth;
	private final int cellHeight;
	private final int hgap;
	private final int vgap;

	public UniformGridLayout(int cellWidth, int cellHeight, int hgap, int vgap)
	{
		this.cellWidth = cellWidth;
		this.cellHeight = cellHeight;
		this.hgap = hgap;
		this.vgap = vgap;
	}

	@Override
	public void addLayoutComponent(String name, Component comp) {}

	@Override
	public void removeLayoutComponent(Component comp) {}

	@Override
	public Dimension preferredLayoutSize(Container target)
	{
		synchronized (target.getTreeLock()) {
			Insets insets = target.getInsets();
			int count = getVisibleCount(target);

			if (count == 0)
				return new Dimension(insets.left + insets.right, insets.top + insets.bottom);

			int cols = getColumns(target);
			if (cols < 1)
				cols = 1;
			int rows = (count + cols - 1) / cols;

			int width = cols * cellWidth + (cols - 1) * hgap + insets.left + insets.right;
			int height = rows * cellHeight + (rows - 1) * vgap + insets.top + insets.bottom;

			return new Dimension(width, height);
		}
	}

	@Override
	public Dimension minimumLayoutSize(Container target)
	{
		Insets insets = target.getInsets();
		return new Dimension(cellWidth + insets.left + insets.right, cellHeight + insets.top + insets.bottom);
	}

	@Override
	public void layoutContainer(Container target)
	{
		synchronized (target.getTreeLock()) {
			Insets insets = target.getInsets();
			int cols = getColumns(target);
			if (cols < 1)
				cols = 1;

			int x = insets.left;
			int y = insets.top;
			int col = 0;

			for (int i = 0; i < target.getComponentCount(); i++) {
				Component c = target.getComponent(i);
				if (!c.isVisible())
					continue;

				c.setBounds(x, y, cellWidth, cellHeight);

				col++;
				if (col >= cols) {
					col = 0;
					x = insets.left;
					y += cellHeight + vgap;
				}
				else {
					x += cellWidth + hgap;
				}
			}
		}
	}

	private int getColumns(Container target)
	{
		Insets insets = target.getInsets();
		int availableWidth = target.getWidth() - insets.left - insets.right;

		// If inside a scroll pane, use the viewport width
		Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
		if (scrollPane != null) {
			JViewport viewport = ((JScrollPane) scrollPane).getViewport();
			if (viewport != null) {
				availableWidth = viewport.getWidth() - insets.left - insets.right;
			}
		}

		if (availableWidth <= 0)
			return 1;

		return Math.max(1, (availableWidth + hgap) / (cellWidth + hgap));
	}

	private int getVisibleCount(Container target)
	{
		int count = 0;
		for (int i = 0; i < target.getComponentCount(); i++) {
			if (target.getComponent(i).isVisible())
				count++;
		}
		return count;
	}
}
