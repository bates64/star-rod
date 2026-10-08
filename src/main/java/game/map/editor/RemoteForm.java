package game.map.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

/**
 * A form another program shows in place of one of Star Rod's dialogs, with fields of the kinds properties have (see
 * {@link RemoteProperties}), which edit its state as they change, such as a preview's options. It's a card over the
 * views, where it doesn't hide what it previews, or else a dialog. Submitting it runs `onSubmit`, and closing it
 * otherwise runs `onCancel`. Its fields' values come from suppliers, and go to consumers, rather than objects.
 */
public class RemoteForm
{
	final String title;
	final String submitLabel;
	boolean card = false;
	final List<RemoteField<Object>> fields = new ArrayList<>();
	/** Runs after any field changes. */
	Runnable onChange = () -> {};
	Runnable onSubmit = () -> {};
	Runnable onCancel = () -> {};

	public RemoteForm(String title, String submitLabel)
	{
		this.title = title;
		this.submitLabel = submitLabel;
	}

	/** Shows the form as a card over the views, which it previews something in. */
	public RemoteForm card()
	{
		card = true;
		return this;
	}

	public RemoteForm onChange(Runnable onChange)
	{
		this.onChange = onChange;
		return this;
	}

	public RemoteForm onSubmit(Runnable onSubmit)
	{
		this.onSubmit = onSubmit;
		return this;
	}

	public RemoteForm onCancel(Runnable onCancel)
	{
		this.onCancel = onCancel;
		return this;
	}

	/**
	 * A label of Star Rod's, which is often in title case, in sentence case, as the other program's labels are. Words
	 * in capitals, such as "UV", stay as they are.
	 */
	public static String sentenceCase(String label)
	{
		String[] words = label.split(" ");
		for (int i = 1; i < words.length; i++) {
			String word = words[i];
			if (!word.equals(word.toUpperCase()))
				words[i] = word.toLowerCase();
		}
		return String.join(" ", words);
	}

	public static String[] sentenceCase(Object[] labels)
	{
		String[] cased = new String[labels.length];
		for (int i = 0; i < labels.length; i++)
			cased[i] = sentenceCase(String.valueOf(labels[i]));
		return cased;
	}

	private RemoteField<Object> add(RemoteField<Object> field)
	{
		fields.add(field);
		return field;
	}

	public RemoteField<Object> heading(String key, String label)
	{
		return add(new RemoteField<>(key, "heading", label));
	}

	public RemoteField<Object> info(String key, Supplier<String> text)
	{
		return add(new RemoteField<>(key, "info", "").get(obj -> new JsonPrimitive(text.get())));
	}

	public RemoteField<Object> checkbox(String key, String label, BooleanSupplier get, Consumer<Boolean> set)
	{
		return add(new RemoteField<>(key, "checkbox", label)
			.get(obj -> new JsonPrimitive(get.getAsBoolean()))
			.set((obj, value) -> {
				set.accept(value.getAsBoolean());
				return null;
			}));
	}

	public RemoteField<Object> text(String key, String label, Supplier<String> get, Consumer<String> set)
	{
		return add(new RemoteField<>(key, "text", label)
			.get(obj -> new JsonPrimitive(get.get()))
			.set((obj, value) -> {
				set.accept(value.getAsString());
				return null;
			}));
	}

	public RemoteField<Object> number(String key, String label, DoubleSupplier get, DoubleConsumer set)
	{
		return add(new RemoteField<>(key, "number", label)
			.get(obj -> new JsonPrimitive(get.getAsDouble()))
			.set((obj, value) -> {
				set.accept(value.getAsDouble());
				return null;
			}));
	}

	public RemoteField<Object> integer(String key, String label, IntSupplier get, IntConsumer set)
	{
		return add(new RemoteField<>(key, "number", label)
			.extra("integer", new JsonPrimitive(true))
			.get(obj -> new JsonPrimitive(get.getAsInt()))
			.set((obj, value) -> {
				set.accept(value.getAsInt());
				return null;
			}));
	}

	/** A choice of one of `options`, by its index. */
	public RemoteField<Object> choice(String key, String label, String[] options, IntSupplier get, IntConsumer set)
	{
		JsonArray names = new JsonArray();
		for (String option : options)
			names.add(option);
		return add(new RemoteField<>(key, "choice", label)
			.options(obj -> names)
			.get(obj -> new JsonPrimitive(get.getAsInt()))
			.set((obj, value) -> {
				set.accept(value.getAsInt());
				return null;
			}));
	}

	/** Numbers labelled by `components`, which are set one at a time, by index. */
	public RemoteField<Object> vector(String key, String label, String[] components, boolean integer,
		Supplier<double[]> get, BiConsumer<Integer, Double> set)
	{
		JsonArray labels = new JsonArray();
		for (String component : components)
			labels.add(component);
		return add(new RemoteField<>(key, "vector", label)
			.extra("components", labels)
			.extra("integer", new JsonPrimitive(integer))
			.get(obj -> {
				JsonArray values = new JsonArray();
				for (double v : get.get())
					values.add(v);
				return values;
			})
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				set.accept(change.get(0).getAsInt(), change.get(1).getAsDouble());
				return null;
			}));
	}

	/**
	 * A file's path, which the other program can choose as `mode` "open" or "save", with one of `extensions`, or
	 * type.
	 */
	public RemoteField<Object> file(String key, String label, String mode, String[] extensions, Supplier<String> get,
		Consumer<String> set)
	{
		JsonArray exts = new JsonArray();
		for (String ext : extensions)
			exts.add(ext);
		return add(new RemoteField<>(key, "file", label)
			.extra("mode", mode)
			.extra("extensions", exts)
			.get(obj -> new JsonPrimitive(get.get()))
			.set((obj, value) -> {
				set.accept(value.getAsString());
				return null;
			}));
	}

	public RemoteField<Object> button(String key, String label, Runnable run)
	{
		return add(new RemoteField<>(key, "button", label).set((obj, value) -> {
			run.run();
			return null;
		}));
	}
}
