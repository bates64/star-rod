package common.remote;

import java.awt.Canvas;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import common.KeyboardInput;
import common.MouseInput;

/**
 * An editor's views, shown by another program over a {@link RemoteSession} in place of the editor's window: what the
 * editor draws goes to the session, and the session's input comes to the editor as its window's would. The editor
 * draws between {@link #beginFrame} and {@link #endFrame}, and calls {@link #receive} each step.
 */
public class RemoteView
{
	private final RemoteSession session;
	private final Canvas canvas;
	private final KeyboardInput keyboard;
	private final MouseInput mouse;
	private final KeyEventDispatcher keyDispatcher;
	private final MouseInput.RemotePointer pointer;
	private final RemoteOutput output = new RemoteOutput();

	private boolean focused = false;
	// the editor's menus, which the other program can show and click, and what was last sent of them
	private JMenuBar menuBar = null;
	private String menuSent = null;
	private int receiveCount = 0;
	private boolean visible = true;
	// where each mouse button was pressed, which a release at the same place makes a click, as AWT has it
	private final Point[] pressedAt = new Point[4];

	/**
	 * `canvas` is what the editor draws on, which takes the size the session asks for. `keyDispatcher`, if not null,
	 * also gets each key event, as the editor's window's focus manager would give it.
	 */
	public RemoteView(RemoteSession session, Canvas canvas, KeyboardInput keyboard, MouseInput mouse,
		KeyEventDispatcher keyDispatcher)
	{
		this.session = session;
		this.canvas = canvas;
		this.keyboard = keyboard;
		this.mouse = mouse;
		this.keyDispatcher = keyDispatcher;

		pointer = new MouseInput.RemotePointer(grabbed -> {
			JsonObject message = new JsonObject();
			message.addProperty("type", "grab");
			message.addProperty("grabbed", grabbed);
			session.send(message);
		});
		mouse.setRemote(pointer);
	}

	public RemoteSession getSession()
	{
		return session;
	}

	public boolean isClosed()
	{
		return session.isClosed();
	}

	/**
	 * Offers the session the editor's menus. It's sent each item, by its path of labels such as "Map/Has Background",
	 * whenever which items are enabled or checked changes, and can click them.
	 */
	public void setMenuBar(JMenuBar menuBar)
	{
		this.menuBar = menuBar;
	}

	/** Whether the other program shows the views, which the editor needn't draw while it doesn't. */
	public boolean isVisible()
	{
		return visible;
	}

	/** Whether the other program's view of the editor has the keyboard. */
	public boolean isFocused()
	{
		return focused;
	}

	/** Makes the session's frame the screen. Needs the editor's GL context. */
	public void beginFrame()
	{
		output.begin(canvas.getWidth(), canvas.getHeight());
	}

	/** Sends what was drawn since {@link #beginFrame}, if it changed. */
	public void endFrame()
	{
		output.end(session);
	}

	/**
	 * Acts on what the session sent since the last call. Messages for the editor, rather than its views, go to
	 * `editorMessages`.
	 */
	public void receive(Consumer<JsonObject> editorMessages)
	{
		JsonObject message;
		while ((message = session.poll()) != null) {
			switch (message.get("type").getAsString()) {
				case "resize": {
					int width = message.get("width").getAsInt();
					int height = message.get("height").getAsInt();
					SwingUtilities.invokeLater(() -> canvas.setSize(width, height));
					break;
				}
				case "show":
					visible = true;
					output.resend();
					break;
				case "hide":
					visible = false;
					break;
				case "focus":
					focused = message.get("focused").getAsBoolean();
					break;
				case "pointer":
					pointer.update(
						message.get("inside").getAsBoolean(),
						message.get("x").getAsInt(),
						message.get("y").getAsInt(),
						message.get("movedX").getAsInt(),
						message.get("movedY").getAsInt());
					break;
				case "button":
					receiveButton(message);
					break;
				case "wheel":
					mouse.mouseWheelMoved(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
						modifiers(message), mouse.getPosX(), mouse.getPosY(), 0, false,
						MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, message.get("rotation").getAsInt()));
					break;
				case "key":
					receiveKey(message);
					break;
				case "menu":
					clickMenuItem(message.get("path").getAsString());
					break;
				default:
					editorMessages.accept(message);
			}
		}

		// a few times a second
		if (menuBar != null && receiveCount++ % 15 == 0)
			sendMenu();
	}

	/** Sends the menus, if which items are enabled or checked changed. */
	private void sendMenu()
	{
		JsonArray items = new JsonArray();
		for (MenuElement menu : menuBar.getSubElements())
			describeMenuItems(menu, "", items);
		String menu = items.toString();
		if (!menu.equals(menuSent)) {
			menuSent = menu;
			JsonObject message = new JsonObject();
			message.addProperty("type", "menu");
			message.add("items", items);
			session.send(message);
		}
	}

	private static void describeMenuItems(MenuElement element, String parentPath, JsonArray items)
	{
		if (!(element instanceof JMenuItem item)) {
			for (MenuElement child : element.getSubElements())
				describeMenuItems(child, parentPath, items);
			return;
		}
		String path = parentPath + item.getText().trim();
		if (item instanceof JMenu) {
			for (MenuElement child : item.getSubElements())
				describeMenuItems(child, path + "/", items);
			return;
		}
		JsonObject json = new JsonObject();
		json.addProperty("path", path);
		json.addProperty("enabled", item.isEnabled());
		String keybinding = keybinding(item.getAccelerator());
		if (keybinding != null)
			json.addProperty("keybinding", keybinding);
		if (item instanceof JCheckBoxMenuItem || item instanceof JRadioButtonMenuItem)
			json.addProperty("checked", item.isSelected());
		items.add(json);
	}

	/**
	 * A menu item's shortcut as the other program writes a keybinding, such as "shift+g" or "f2", or null if it has
	 * none, or one with a key that has no name here.
	 */
	private static String keybinding(KeyStroke stroke)
	{
		if (stroke == null)
			return null;
		int code = stroke.getKeyCode();
		String key;
		if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z || code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)
			key = String.valueOf((char) code).toLowerCase();
		else if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12)
			key = "f" + (code - KeyEvent.VK_F1 + 1);
		else
			key = switch (code) {
				case KeyEvent.VK_MINUS -> "-";
				case KeyEvent.VK_EQUALS -> "=";
				case KeyEvent.VK_COMMA -> ",";
				case KeyEvent.VK_PERIOD -> ".";
				case KeyEvent.VK_SLASH -> "/";
				case KeyEvent.VK_OPEN_BRACKET -> "[";
				case KeyEvent.VK_CLOSE_BRACKET -> "]";
				case KeyEvent.VK_DELETE -> "delete";
				case KeyEvent.VK_BACK_SPACE -> "backspace";
				case KeyEvent.VK_ESCAPE -> "escape";
				case KeyEvent.VK_ENTER -> "enter";
				case KeyEvent.VK_SPACE -> "space";
				case KeyEvent.VK_TAB -> "tab";
				default -> null;
			};
		if (key == null)
			return null;

		int modifiers = stroke.getModifiers();
		StringBuilder keybinding = new StringBuilder();
		if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0)
			keybinding.append("ctrl+");
		if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0)
			keybinding.append("shift+");
		if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0)
			keybinding.append("alt+");
		if ((modifiers & InputEvent.META_DOWN_MASK) != 0)
			keybinding.append("meta+");
		return keybinding.append(key).toString();
	}

	/** Clicks the menu item at a path of labels, as a user would, on Swing's thread. */
	private void clickMenuItem(String path)
	{
		if (menuBar == null)
			return;
		SwingUtilities.invokeLater(() -> {
			JMenuItem item = findMenuItem(menuBar, "", path);
			if (item != null && item.isEnabled())
				item.doClick(0);
		});
	}

	private static JMenuItem findMenuItem(MenuElement element, String parentPath, String path)
	{
		for (MenuElement child : element.getSubElements()) {
			String childPath = parentPath;
			if (child instanceof JMenuItem item) {
				childPath = parentPath + item.getText().trim();
				if (!(item instanceof JMenu) && childPath.equals(path))
					return item;
				childPath += "/";
			}
			JMenuItem found = findMenuItem(child, childPath, path);
			if (found != null)
				return found;
		}
		return null;
	}

	/** A button press or release, as AWT button 1 (left), 2 (middle), or 3 (right). */
	private void receiveButton(JsonObject message)
	{
		int button = message.get("button").getAsInt();
		boolean down = message.get("down").getAsBoolean();
		int x = message.get("x").getAsInt();
		int y = message.get("y").getAsInt();
		long when = System.currentTimeMillis();
		int modifiers = modifiers(message);

		if (down) {
			pressedAt[button] = new Point(x, y);
			mouse.mousePressed(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, when, modifiers, x, y, 1, false, button));
		}
		else {
			mouse.mouseReleased(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, when, modifiers, x, y, 1, false, button));
			if (new Point(x, y).equals(pressedAt[button]))
				mouse.mouseClicked(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, when, modifiers, x, y, 1, false, button));
			pressedAt[button] = null;
		}
	}

	/** A key press or release, by its AWT key code. */
	private void receiveKey(JsonObject message)
	{
		boolean down = message.get("down").getAsBoolean();
		String typed = message.has("char") ? message.get("char").getAsString() : "";
		KeyEvent evt = new KeyEvent(canvas, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
			System.currentTimeMillis(), modifiers(message), message.get("code").getAsInt(),
			typed.length() == 1 ? typed.charAt(0) : KeyEvent.CHAR_UNDEFINED);
		if (down)
			keyboard.keyPressed(evt);
		else
			keyboard.keyReleased(evt);
		if (keyDispatcher != null)
			keyDispatcher.dispatchKeyEvent(evt);
	}

	/** The AWT modifier mask for an input message's shift, ctrl, alt, and meta. */
	private static int modifiers(JsonObject message)
	{
		int modifiers = 0;
		if (message.has("shift") && message.get("shift").getAsBoolean())
			modifiers |= InputEvent.SHIFT_DOWN_MASK;
		if (message.has("ctrl") && message.get("ctrl").getAsBoolean())
			modifiers |= InputEvent.CTRL_DOWN_MASK;
		if (message.has("alt") && message.get("alt").getAsBoolean())
			modifiers |= InputEvent.ALT_DOWN_MASK;
		if (message.has("meta") && message.get("meta").getAsBoolean())
			modifiers |= InputEvent.META_DOWN_MASK;
		return modifiers;
	}
}
