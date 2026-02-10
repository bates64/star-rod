package app.pane;

import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;
import util.ui.Squircle;

/** Vertical tabs. */
public class Dock extends Pane
{
	private static final int TAB_COLUMN_WIDTH = 30;
	private static final int ARC = 10;

	private final List<DockTab> tabs = new ArrayList<>();
	private final List<TabButton> buttons = new ArrayList<>();
	private final JPanel tabColumn;
	private final JPanel contentPanel;
	private final CardLayout cardLayout;
	private int selectedIndex = -1;

	public Dock()
	{
		super(ARC);
		setLayout(new MigLayout("ins 0, fill, gap 2 0", "[" + TAB_COLUMN_WIDTH + "!][grow,fill]", "[grow,fill]"));

		tabColumn = new JPanel(new MigLayout("ins " + ARC + " 0, wrap, gap 0", "[" + TAB_COLUMN_WIDTH + "!]", ""));
		tabColumn.setOpaque(false);
		add(tabColumn, "growy");

		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);
		contentPanel.setOpaque(false);
		add(contentPanel, "grow");

		addTab(new app.pane.explorer.Tab());
		addTab(new app.pane.logs.Tab());

		if (!tabs.isEmpty()) {
			selectTab(0);
			// Account for tab column width and gap in Dock's layout
			Dimension tabSize = tabs.get(0).getPreferredSize();
			setPreferredSize(new Dimension(
				tabSize.width + TAB_COLUMN_WIDTH + 2, // +2 for layout gap
				tabSize.height
			));
		}
	}

	public void addTab(DockTab tab)
	{
		int index = tabs.size();
		tabs.add(tab);

		var button = new TabButton(tab, index);
		buttons.add(button);
		tabColumn.add(button, "growx, h 24!, gapleft 1");

		contentPanel.add(tab, "tab" + index);
	}

	private void selectTab(int index)
	{
		if (index == selectedIndex)
			return;

		selectedIndex = index;
		cardLayout.show(contentPanel, "tab" + index);

		for (var button : buttons)
			button.repaint();
	}

	public void dispose()
	{
		for (var tab : tabs)
			tab.dispose();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int w = getWidth();
		int h = getHeight();
		int tabW = TAB_COLUMN_WIDTH + 2;

		// Create the full squircle for the Dock
		var fullShape = Squircle.path(0, 0, w, h, ARC, ARC, ARC, ARC);

		// Create the tabColumn area (rounded only on left) to exclude from background
		var tabColumnShape = Squircle.path(0, 0, tabW, h, ARC, 0, 0, ARC);

		// Subtract tabColumn from full shape
		var area = new Area(fullShape);
		area.subtract(new Area(tabColumnShape));

		// Draw background only in the content area
		g2.setColor(UIManager.getColor("TabbedPane.background"));
		g2.fill(area);

		// Draw 1px border for tabColumn (left, top, bottom only, no right)
		g2.setColor(UIManager.getColor("Component.borderColor"));
		g2.setStroke(new BasicStroke(1));

		var borderPath = new Path2D.Double();
		double x = 0.5;
		double y = 0.5;
		double bw = TAB_COLUMN_WIDTH + 1;
		double bh = h - 1;
		double arc = ARC;

		// Start at top-right
		borderPath.moveTo(x + bw, y);
		// Top edge to top-left corner
		borderPath.lineTo(x + arc, y);
		// Top-left corner
		borderPath.quadTo(x, y, x, y + arc);
		// Left edge
		borderPath.lineTo(x, y + bh - arc);
		// Bottom-left corner
		borderPath.quadTo(x, y + bh, x + arc, y + bh);
		// Bottom edge to bottom-right
		borderPath.lineTo(x + bw, y + bh);

		g2.draw(borderPath);

		g2.dispose();
	}

	private class TabButton extends JComponent
	{
		private static final int INDICATOR_WIDTH = 2;
		private static final int HOVER_SIZE = 20;

		private final DockTab tab;
		private final int index;
		private boolean hovered = false;

		TabButton(DockTab tab, int index)
		{
			this.tab = tab;
			this.index = index;
			setPreferredSize(new Dimension(TAB_COLUMN_WIDTH, 24));
			setToolTipText(tab.getTabName());

			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e)
				{
					selectTab(TabButton.this.index);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					hovered = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hovered = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			var g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			// Selection indicator
			if (index == selectedIndex) {
				g2.setColor(UIManager.getColor("Component.focusColor"));
				int barH = getHeight();
				int barY = (getHeight() - barH) / 2;
				g2.fillRect(0, barY, INDICATOR_WIDTH, barH);
			}

			// Hover squircle
			if (hovered) {
				int hoverX = INDICATOR_WIDTH + (getWidth() - INDICATOR_WIDTH - HOVER_SIZE) / 2;
				int hoverY = (getHeight() - HOVER_SIZE) / 2;
				var hoverShape = Squircle.path(hoverX, hoverY, HOVER_SIZE, HOVER_SIZE, 4);
				g2.setColor(UIManager.getColor("Component.borderColor"));
				g2.fill(hoverShape);
			}

			// Icon (centered accounting for indicator space)
			var icon = tab.getTabIcon();
			if (icon != null) {
				int iconX = INDICATOR_WIDTH + (getWidth() - INDICATOR_WIDTH - icon.getIconWidth()) / 2;
				int iconY = (getHeight() - icon.getIconHeight()) / 2;
				icon.paintIcon(this, g2, iconX, iconY);
			}

			g2.dispose();
		}
	}
}
