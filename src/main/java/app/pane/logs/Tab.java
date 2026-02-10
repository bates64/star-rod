package app.pane.logs;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import app.pane.DockTab;
import net.miginfocom.swing.MigLayout;
import util.Logger;
import util.ui.ThemedIcon;

public class Tab extends DockTab
{
	private final JTextArea textArea;
	private final Logger.Listener listener;

	public Tab()
	{
		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		var scrollPane = new JScrollPane(textArea);
		scrollPane.setBorder(null);

		setLayout(new MigLayout("fill, ins 0"));
		add(scrollPane, "grow");

		listener = msg -> {
			textArea.append(msg.text + System.lineSeparator());
			textArea.setCaretPosition(textArea.getDocument().getLength());
		};
		Logger.addListener(listener);
	}

	@Override
	public Icon getTabIcon()
	{
		return ThemedIcon.TERMINAL_24.derive(15, 15);
	}

	@Override
	public String getTabName()
	{
		return "Logs";
	}

	@Override
	public void dispose()
	{
		Logger.removeListener(listener);
	}
}
