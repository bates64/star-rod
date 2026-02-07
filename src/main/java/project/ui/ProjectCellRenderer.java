package project.ui;

import java.awt.Component;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import app.SwingUtils;
import project.Project;
import net.miginfocom.swing.MigLayout;

/**
 * Cell renderer for projects in the project list.
 * Displays project name (bold), path, and last opened time.
 */
public class ProjectCellRenderer extends JPanel implements ListCellRenderer<Project>
{
	private final JLabel nameLabel;
	private final JLabel pathLabel;
	private final JLabel timeLabel;

	public ProjectCellRenderer()
	{
		nameLabel = new JLabel("");
		SwingUtils.setFontSize(nameLabel, 14);

		pathLabel = new JLabel("");
		SwingUtils.setFontSize(pathLabel, 11);

		timeLabel = new JLabel("");
		SwingUtils.setFontSize(timeLabel, 11);

		setLayout(new MigLayout("ins 0, fillx", "[grow]8[120!]"));
		add(nameLabel, "wrap");
		add(pathLabel, "");
		add(timeLabel, "align right");

		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
	}

	@Override
	public Component getListCellRendererComponent(
		JList<? extends Project> list,
		Project project,
		int index,
		boolean isSelected,
		boolean cellHasFocus)
	{
		if (isSelected) {
			setBackground(list.getSelectionBackground());
			setForeground(list.getSelectionForeground());
			nameLabel.setForeground(list.getSelectionForeground());
			pathLabel.setForeground(list.getSelectionForeground());
			timeLabel.setForeground(list.getSelectionForeground());
		}
		else {
			setBackground(list.getBackground());
			setForeground(list.getForeground());
			nameLabel.setForeground(list.getForeground());
			pathLabel.setForeground(SwingUtils.getGrayTextColor());
			timeLabel.setForeground(SwingUtils.getGrayTextColor());
		}

		if (project != null) {
			nameLabel.setText(project.getName());
			pathLabel.setText(project.getPath().getAbsolutePath());
			timeLabel.setText(formatRelativeTime(project.getLastOpened()));
		}
		else {
			nameLabel.setText("ERROR");
			pathLabel.setText("");
			timeLabel.setText("");
			nameLabel.setForeground(SwingUtils.getRedTextColor());
		}

		return this;
	}

	/**
	 * Formats a timestamp as relative time (e.g., "2 hours ago", "Yesterday").
	 */
	private static String formatRelativeTime(long timestamp)
	{
		LocalDateTime time = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
		LocalDateTime now = LocalDateTime.now();

		long minutes = ChronoUnit.MINUTES.between(time, now);
		long hours = ChronoUnit.HOURS.between(time, now);
		long days = ChronoUnit.DAYS.between(time, now);

		if (minutes < 1) {
			return "Just now";
		}
		else if (minutes < 60) {
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		}
		else if (hours < 24) {
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		}
		else if (days == 1) {
			return "Yesterday";
		}
		else if (days < 7) {
			return days + " days ago";
		}
		else if (days < 30) {
			long weeks = days / 7;
			return weeks + (weeks == 1 ? " week ago" : " weeks ago");
		}
		else if (days < 365) {
			long months = days / 30;
			return months + (months == 1 ? " month ago" : " months ago");
		}
		else {
			long years = days / 365;
			return years + (years == 1 ? " year ago" : " years ago");
		}
	}
}
