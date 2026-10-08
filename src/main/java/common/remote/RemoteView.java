package common.remote;

import java.awt.Canvas;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

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
				default:
					editorMessages.accept(message);
			}
		}
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
