package app.bar;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;
import project.build.BuildStatusBar;

/** A horizontal bar component for displaying status information. */
public class Bar extends JPanel
{
	private final GitBranch gitBranch;
	private BuildStatusBar buildStatusBar;

	public Bar()
	{
		setOpaque(false);
		setLayout(new MigLayout("fill, ins 4, gap 8"));

		gitBranch = new GitBranch();
		add(gitBranch, "alignx left");

		// Build status bar will be added later via setBuildStatusBar
	}

	public void setBuildStatusBar(BuildStatusBar buildStatusBar)
	{
		if (this.buildStatusBar != null) {
			remove(this.buildStatusBar);
		}

		this.buildStatusBar = buildStatusBar;
		add(buildStatusBar, "alignx right");
		revalidate();
		repaint();
	}

	public void dispose()
	{
		gitBranch.dispose();
	}
}
