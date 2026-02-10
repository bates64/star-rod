package app.pane.explorer;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import app.pane.Pane;
import net.miginfocom.swing.MigLayout;
import util.ui.ThemedIcon;

class SearchField extends Pane
{
	private static final int ICON_SIZE = 15;
	private static final int ICON_PADDING = 8;

	private final JTextField textField;

	SearchField()
	{
		super(6);
		setLayout(new MigLayout("ins 0, fill", "[grow]", "[]"));

		textField = new JTextField();
		textField.setOpaque(false);
		textField.setBorder(new EmptyBorder(4, 8, 4, ICON_SIZE + ICON_PADDING * 2));
		textField.putClientProperty("JTextField.placeholderText", "Search...");
		add(textField, "grow");

		setBackground(textField.getBackground());
	}

	@Override
	protected void paintChildren(Graphics g)
	{
		super.paintChildren(g);

		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int iconX = getWidth() - ICON_SIZE - ICON_PADDING;
		int iconY = (getHeight() - ICON_SIZE) / 2;

		ThemedIcon.SEARCH.derive(ICON_SIZE, ICON_SIZE).paintIcon(this, g2, iconX, iconY);
		g2.dispose();
	}

	String getText()
	{
		return textField.getText();
	}
}
