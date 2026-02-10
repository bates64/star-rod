package app.pane.explorer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragSource;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLayeredPane;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import app.SwingUtils;
import assets.AssetHandle;
import util.Logger;

abstract class Item extends JPanel
{
	static final int PADDING = 3;
	static final int SIZE = AssetHandle.THUMBNAIL_WIDTH + PADDING * 2;

	final Tab explorer;
	final String name;

	private Icon icon;
	protected boolean checkerboard;
	boolean selected;
	boolean dropTarget;
	private JTextField renameField;

	private static final JPopupMenu contextMenu = buildContextMenu();
	private static Item popupItem;

	Item(Tab explorer, String name, Icon defaultIcon, boolean checkerboard)
	{
		this.explorer = explorer;
		this.name = name;
		this.icon = defaultIcon;
		this.checkerboard = checkerboard;

		setLayout(null);
		setPreferredSize(new Dimension(SIZE, SIZE));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));

		var adapter = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
					showContextMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
					showContextMenu(e);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e)) {
					if (e.getClickCount() == 1)
						explorer.select(Item.this);
					else if (e.getClickCount() == 2)
						onDoubleClick();
				}
			}
		};
		addMouseListener(adapter);

		if (isDraggable()) {
			var dragSource = new DragSource();
			dragSource.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, (DragGestureEvent dge) -> {
				Transferable transferable = createDragTransferable();
				if (transferable != null)
					dge.startDrag(DragSource.DefaultMoveDrop, transferable);
			});
		}
	}

	void setIcon(Icon icon)
	{
		this.icon = icon;
		repaint();
	}

	abstract void onDoubleClick();

	boolean isDraggable()
	{
		return false;
	}

	Transferable createDragTransferable()
	{
		return null;
	}

	/** Override to return the asset for context menu operations. */
	AssetHandle getAsset()
	{
		return null;
	}

	private void showContextMenu(MouseEvent e)
	{
		AssetHandle asset = getAsset();
		if (asset == null)
			return;

		explorer.select(this);
		popupItem = this;
		contextMenu.show(this, e.getX(), e.getY());
	}

	private static JPopupMenu buildContextMenu()
	{
		var menu = new JPopupMenu();

		var renameItem = new JMenuItem("Rename");
		renameItem.addActionListener(e -> {
			if (popupItem != null)
				popupItem.onRename();
		});
		menu.add(renameItem);

		var deleteItem = new JMenuItem("Delete");
		deleteItem.addActionListener(e -> {
			if (popupItem != null)
				popupItem.onDelete();
		});
		menu.add(deleteItem);

		return menu;
	}

	void cancelRename()
	{
		if (renameField == null)
			return;
		var layeredPane = getRootPane().getLayeredPane();
		layeredPane.remove(renameField);
		layeredPane.repaint();
		renameField = null;
		repaint();
	}

	private void onRename()
	{
		AssetHandle asset = getAsset();
		if (asset == null || renameField != null)
			return;

		String currentName = asset.getAssetName();

		Insets ins = getInsets();
		int x = ins.left;
		int w = getWidth() - ins.left - ins.right;
		int h = getHeight() - ins.top - ins.bottom;
		int iconAreaH = h * 4 / 5;
		int labelY = ins.top + iconAreaH;
		int labelH = h - iconAreaH;

		renameField = new JTextField(currentName);
		renameField.setHorizontalAlignment(JTextField.CENTER);
		renameField.selectAll();
		renameField.setFont(renameField.getFont().deriveFont(11f));
		renameField.setBorder(null);
		renameField.setMargin(new Insets(0, 0, 0, 0));

		final JLayeredPane layeredPane = getRootPane().getLayeredPane();
		final int localCenterX = x + w / 2;
		final int localY = labelY;

		Runnable resizeRenameField = () -> {
			FontMetrics fm = renameField.getFontMetrics(renameField.getFont());
			int textW = fm.stringWidth(renameField.getText()) + fm.charWidth('W');
			int fieldW = Math.max(w, textW);
			Point origin = SwingUtilities.convertPoint(Item.this, localCenterX - fieldW / 2, localY, layeredPane);
			renameField.setBounds(origin.x, origin.y, fieldW, labelH);
		};
		resizeRenameField.run();

		renameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { resizeRenameField.run(); }
			@Override
			public void removeUpdate(DocumentEvent e) { resizeRenameField.run(); }
			@Override
			public void changedUpdate(DocumentEvent e) { resizeRenameField.run(); }
		});

		Runnable commit = () -> {
			if (renameField == null)
				return;
			String newName = renameField.getText().trim();
			cancelRename();

			if (newName.isEmpty() || newName.equals(currentName)
				|| newName.contains("/") || newName.contains("\\"))
				return;

			if (!asset.renameAsset(newName)) {
				SwingUtils.getErrorDialog()
					.setTitle("Rename Failed")
					.setMessage("Could not rename " + currentName + " to " + newName + ".")
					.show();
			}
		};

		renameField.addActionListener(e -> commit.run());

		renameField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});

		renameField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
		renameField.getActionMap().put("cancel", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e)
			{
				cancelRename();
			}
		});

		layeredPane.add(renameField, JLayeredPane.POPUP_LAYER);
		layeredPane.revalidate();
		renameField.requestFocusInWindow();
	}

	private void onDelete()
	{
		AssetHandle asset = getAsset();
		if (asset == null)
			return;

		String assetName = asset.getAssetName();
		int result = SwingUtils.getConfirmDialog()
			.setTitle("Delete")
			.setMessage("Delete " + assetName + "?")
			.setOptionsType(JOptionPane.YES_NO_OPTION)
			.choose();

		if (result != JOptionPane.YES_OPTION)
			return;

		if (!asset.deleteAsset()) {
			SwingUtils.getErrorDialog()
				.setTitle("Delete Failed")
				.setMessage("Could not delete " + assetName + ".")
				.show();
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		var g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Selected background
		if (selected) {
			g2.setColor(UIManager.getColor("Component.borderColor"));
			int arc = UIManager.getInt("Button.arc");
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
		}

		Insets ins = getInsets();
		int x = ins.left;
		int y = ins.top;
		int w = getWidth() - ins.left - ins.right;
		int h = getHeight() - ins.top - ins.bottom;

		int iconAreaH = h * 4 / 5;
		int labelAreaH = h - iconAreaH;

		// Icon
		if (icon != null) {
			int iconW = icon.getIconWidth();
			int iconH = icon.getIconHeight();
			int iconX = x + (w - iconW) / 2;
			int iconY = y + (iconAreaH - iconH) / 2;

			if (checkerboard)
				paintCheckerboard(g2, iconX, iconY, iconW, iconH);

			icon.paintIcon(this, g2, iconX, iconY);
		}

		// Name label with ellipsis (hidden during inline rename)
		if (renameField == null) {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setColor(UIManager.getColor("Label.foreground"));
			g2.setFont(getFont().deriveFont(11f));
			FontMetrics fm = g2.getFontMetrics();

			String displayText = name;
			int textW = fm.stringWidth(displayText);
			if (textW > w) {
				String ellipsis = "...";
				int ellipsisW = fm.stringWidth(ellipsis);
				int maxW = w - ellipsisW;
				int len = displayText.length();
				while (len > 0 && fm.stringWidth(displayText.substring(0, len)) > maxW)
					len--;
				displayText = displayText.substring(0, len) + ellipsis;
				textW = fm.stringWidth(displayText);
			}

			int textX = x + (w - textW) / 2;
			int textY = y + iconAreaH + (labelAreaH + fm.getAscent() - fm.getDescent()) / 2;
			g2.drawString(displayText, textX, textY);
		}

		// Drop target highlight
		if (dropTarget) {
			g2.setColor(UIManager.getColor("Component.focusColor"));
			g2.setStroke(new BasicStroke(2));
			g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
		}

		g2.dispose();
	}

	private static void paintCheckerboard(Graphics2D g2, int x, int y, int w, int h)
	{
		Color panelBg = UIManager.getColor("Panel.background");
		boolean dark = panelBg != null && luminance(panelBg) < 0.5f;
		Color c1 = dark ? new Color(0x3C3C3C) : new Color(0xCCCCCC);
		Color c2 = dark ? new Color(0x2C2C2C) : new Color(0xFFFFFF);
		int cs = 4;
		var tile = new BufferedImage(cs * 2, cs * 2, BufferedImage.TYPE_INT_RGB);
		Graphics2D tg = tile.createGraphics();
		tg.setColor(c1);
		tg.fillRect(0, 0, cs, cs);
		tg.fillRect(cs, cs, cs, cs);
		tg.setColor(c2);
		tg.fillRect(cs, 0, cs, cs);
		tg.fillRect(0, cs, cs, cs);
		tg.dispose();
		g2.setPaint(new TexturePaint(tile, new Rectangle(x, y, cs * 2, cs * 2)));
		g2.fillRect(x, y, w, h);
	}

	private static float luminance(Color c)
	{
		return (0.299f * c.getRed() + 0.587f * c.getGreen() + 0.114f * c.getBlue()) / 255f;
	}
}
