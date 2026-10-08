package common.remote;

import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Asks the user something in another program over a {@link RemoteSession}, rather than in a dialog of Swing's: for a
 * file, as a "chooseFile" message, to choose one of a message's buttons, as an "ask" message, or to pick one of a
 * list of items, as a "pick" message. Each waits for the other program's reply, a "fileChosen" or "answered" message,
 * which comes on the session's reader thread. Swing's thread keeps handling events meanwhile, if it's the one
 * waiting.
 */
public class RemoteDialogs
{
	private static RemoteDialogs instance = null;

	private final RemoteSession session;
	// requests not yet answered, by ID
	private final Map<Integer, CompletableFuture<JsonObject>> requests = new ConcurrentHashMap<>();
	private final AtomicInteger nextRequest = new AtomicInteger();

	private RemoteDialogs(RemoteSession session)
	{
		this.session = session;
		session.setImmediateHandler(message -> {
			String type = message.get("type").getAsString();
			if (!type.equals("fileChosen") && !type.equals("answered"))
				return false;
			CompletableFuture<JsonObject> request = requests.remove(message.get("id").getAsInt());
			if (request != null)
				request.complete(message);
			return true;
		});
	}

	/** Asks the user in `session`'s program from then on. */
	public static void start(RemoteSession session)
	{
		instance = new RemoteDialogs(session);
	}

	/** Whether another program asks the user, rather than Swing's dialogs. */
	public static boolean isActive()
	{
		return instance != null;
	}

	/**
	 * Has the other program ask the user for a file, as `mode` "open" or "save", or a folder, as "folder", starting in
	 * `dir`. Returns the path chosen, or null if they cancelled.
	 */
	public static String chooseFile(String mode, String title, File dir, String ... extensions)
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "chooseFile");
		message.addProperty("mode", mode);
		message.addProperty("title", title);
		if (dir != null)
			message.addProperty("dir", dir.getAbsolutePath());
		JsonArray exts = new JsonArray();
		for (String ext : extensions)
			exts.add(ext);
		message.add("extensions", exts);

		JsonObject reply = instance.request(message);
		return (reply != null && reply.has("path")) ? reply.get("path").getAsString() : null;
	}

	/**
	 * Has the other program show a message, as `kind` "info", "question", "warning", or "error", with `buttons`.
	 * Returns the index of the button chosen, or -1 if the message was dismissed.
	 */
	public static int ask(String kind, String title, String text, String ... buttons)
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "ask");
		message.addProperty("kind", kind);
		message.addProperty("title", title);
		message.addProperty("message", text);
		JsonArray labels = new JsonArray();
		for (String button : buttons)
			labels.add(button);
		message.add("buttons", labels);

		JsonObject reply = instance.request(message);
		JsonElement index = (reply != null) ? reply.get("index") : null;
		return (index != null && !index.isJsonNull()) ? index.getAsInt() : -1;
	}

	/**
	 * Has the other program ask the user to pick one of `items`, which they can filter by typing, starting at
	 * `current`, if it's one of them. Returns the index of the item picked, or -1 if they cancelled.
	 */
	public static int pick(String title, List<String> items, String current)
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "pick");
		message.addProperty("title", title);
		JsonArray labels = new JsonArray();
		for (String item : items)
			labels.add(item);
		message.add("items", labels);
		if (current != null)
			message.addProperty("current", current);

		JsonObject reply = instance.request(message);
		JsonElement index = (reply != null) ? reply.get("index") : null;
		return (index != null && !index.isJsonNull()) ? index.getAsInt() : -1;
	}

	/** Sends `message` with an ID, and waits for the reply with that ID. */
	private JsonObject request(JsonObject message)
	{
		int id = nextRequest.getAndIncrement();
		CompletableFuture<JsonObject> request = new CompletableFuture<>();
		requests.put(id, request);
		message.addProperty("id", id);
		session.send(message);

		if (SwingUtilities.isEventDispatchThread()) {
			SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
			request.whenComplete((reply, error) -> loop.exit());
			if (!request.isDone())
				loop.enter();
		}
		try {
			return request.get();
		}
		catch (InterruptedException | ExecutionException e) {
			return null;
		}
	}
}
