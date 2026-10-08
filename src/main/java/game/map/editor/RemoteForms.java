package game.map.editor;

import java.util.List;

import com.google.gson.JsonObject;

import common.commands.AbstractCommand;

/**
 * The form another program shows in place of one of Star Rod's dialogs, one at a time, which it's sent as a "form"
 * message whenever it changes, and as a "formClosed" message once it closes. The other program sets its fields with
 * "formField" messages, and closes it with a "formDone" message, which says whether it was submitted.
 */
public class RemoteForms
{
	// what the form's fields describe, which is the form's own state rather than objects
	private static final List<Object> NO_OBJECTS = List.of(new Object());

	private final MapEditor editor;
	private RemoteForm form = null;
	private int id = 0;
	private String sent = null;

	public RemoteForms(MapEditor editor)
	{
		this.editor = editor;
	}

	/** Shows `form`, in place of any form already shown, which is cancelled. Can be called from any thread. */
	public void open(RemoteForm form)
	{
		editor.runThreadsafe(() -> {
			if (this.form != null)
				close(false);
			this.form = form;
			id++;
			sent = null;
		});
	}

	/** Sends the form, if it changed. */
	public void send()
	{
		if (form == null)
			return;
		JsonObject message = new JsonObject();
		message.addProperty("type", "form");
		message.addProperty("id", id);
		message.addProperty("title", form.title);
		message.addProperty("submit", form.submitLabel);
		message.addProperty("card", form.card);
		message.add("fields", RemoteField.describe(
			form.fields.stream().filter(field -> field.isShown(NO_OBJECTS.get(0))).toList(), NO_OBJECTS));
		String text = message.toString();
		if (!text.equals(sent)) {
			sent = text;
			editor.remote().getSession().send(message);
		}
	}

	/** Acts on a "formField" or "formDone" message, for the form shown. */
	public void receive(JsonObject message)
	{
		if (form == null || message.get("id").getAsInt() != id)
			return;
		if (message.get("type").getAsString().equals("formDone")) {
			close(message.get("submit").getAsBoolean());
			return;
		}
		String key = message.get("key").getAsString();
		for (RemoteField<Object> field : form.fields) {
			if (field.key.equals(key)) {
				AbstractCommand command = field.setter(null, message.get("value"));
				if (command != null)
					command.exec();
				form.onChange.run();
				// sent again, even if it's the same, so a control that was refused shows its value again
				sent = null;
				return;
			}
		}
	}

	private void close(boolean submit)
	{
		RemoteForm closed = form;
		form = null;
		JsonObject message = new JsonObject();
		message.addProperty("type", "formClosed");
		message.addProperty("id", id);
		editor.remote().getSession().send(message);
		if (submit)
			closed.onSubmit.run();
		else
			closed.onCancel.run();
	}
}
