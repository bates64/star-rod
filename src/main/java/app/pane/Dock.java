package app.pane;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import util.Logger;
import util.ui.ThemedIcon;

public class Dock extends JPanel
{
	private JTabbedPane tabbedPane;
	private JTextArea logTextArea;

	public Dock()
	{
		// Create vertical tabbed pane with dock styling
		tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
		tabbedPane.putClientProperty("JTabbedPane.tabType", "card");
		tabbedPane.putClientProperty("JTabbedPane.hasFullBorder", true);

		// Add File Explorer tab with AssetsPanel
		AssetsPanel assetsPanel = new AssetsPanel();
		tabbedPane.addTab(null, ThemedIcon.FOLDER_OPEN_16, assetsPanel);

		// Add Logs tab with text area
		logTextArea = createLogTextArea();
		JScrollPane logScrollPane = new JScrollPane(logTextArea);
		logScrollPane.setBorder(null);
		tabbedPane.addTab(null, ThemedIcon.TERMINAL_16, logScrollPane);
		Logger.addListener(new LogListener(logTextArea));

		// Layout
		setLayout(new BorderLayout());
		add(tabbedPane, BorderLayout.CENTER);
	}

	private JTextArea createLogTextArea()
	{
		JTextArea textArea = new JTextArea();
		textArea.setEditable(false);
		// Add horizontal padding using EmptyBorder
		textArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		return textArea;
	}

	private static class LogListener implements Logger.Listener
	{
		private final JTextArea textArea;

		public LogListener(JTextArea textArea)
		{
			this.textArea = textArea;
		}

		@Override
		public void post(Logger.Message msg)
		{
			textArea.append(msg.text + System.lineSeparator());
			// Auto-scroll to bottom
			textArea.setCaretPosition(textArea.getDocument().getLength());
		}
	}
}
