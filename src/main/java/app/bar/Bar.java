package app.bar;

import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/** A horizontal bar component for displaying status information. */
public class Bar extends JPanel
{
	private final GitBranch gitBranch;

	public Bar()
	{
		setOpaque(false);
		setLayout(new MigLayout("fill, ins 4, gap 8"));

		gitBranch = new GitBranch();
		add(gitBranch, "alignx left");
	}

	public void dispose()
	{
		gitBranch.dispose();
	}
}
