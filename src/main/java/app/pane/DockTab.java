package app.pane;

import javax.swing.Icon;
import javax.swing.JPanel;

public abstract class DockTab extends JPanel
{
	public abstract Icon getTabIcon();

	public abstract String getTabName();

	public void dispose() {}
}
