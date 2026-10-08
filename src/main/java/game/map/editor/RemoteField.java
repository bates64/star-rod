package game.map.editor;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import common.commands.AbstractCommand;
import common.commands.EditableField;

/**
 * A field of objects of type T, which another program shows and edits with a control of its kind, such as a checkbox,
 * and which sets the objects' values by commands. See {@link RemoteProperties} for the kinds of control.
 */
public class RemoteField<T>
{
	final String key;
	final String kind;
	Function<T, String> label;
	Predicate<T> shown = obj -> true;
	boolean single = false;
	Function<T, JsonElement> get = obj -> null;
	BiFunction<T, JsonElement, AbstractCommand> set = (obj, value) -> null;
	Function<T, JsonArray> options = null;
	Function<T, EditableField<?>> optionalField = null;
	Predicate<T> optional = obj -> true;
	final JsonObject extra = new JsonObject();

	RemoteField(String key, String kind, String label)
	{
		this.key = key;
		this.kind = kind;
		this.label = obj -> label;
	}

	public RemoteField<T> label(Function<T, String> label)
	{
		this.label = label;
		return this;
	}

	public RemoteField<T> shown(Predicate<T> shown)
	{
		Predicate<T> before = this.shown;
		this.shown = obj -> before.test(obj) && shown.test(obj);
		return this;
	}

	/** For one object at a time, such as a button that acts on it. */
	public RemoteField<T> single()
	{
		single = true;
		return this;
	}

	public RemoteField<T> get(Function<T, JsonElement> get)
	{
		this.get = get;
		return this;
	}

	public RemoteField<T> set(BiFunction<T, JsonElement, AbstractCommand> set)
	{
		this.set = set;
		return this;
	}

	public RemoteField<T> options(Function<T, JsonArray> options)
	{
		this.options = options;
		return this;
	}

	/** Its value can be left out, which `field`'s enabled flag is whether it isn't, where `optional` holds. */
	public RemoteField<T> optional(Function<T, EditableField<?>> field, Predicate<T> optional)
	{
		this.optionalField = field;
		this.optional = optional;
		return this;
	}

	public RemoteField<T> extra(String name, JsonElement value)
	{
		extra.add(name, value);
		return this;
	}

	public RemoteField<T> extra(String name, String value)
	{
		return extra(name, new JsonPrimitive(value));
	}

	@SuppressWarnings("unchecked")
	private T cast(Object obj)
	{
		return (T) obj;
	}

	boolean isShown(Object obj)
	{
		return shown.test(cast(obj));
	}

	String labelOf(Object obj)
	{
		return label.apply(cast(obj));
	}

	JsonElement valueOf(Object obj)
	{
		return get.apply(cast(obj));
	}

	JsonArray optionsOf(Object obj)
	{
		return (options == null) ? null : options.apply(cast(obj));
	}

	/** Whether its value is used, if it can be left out. */
	Boolean enabledOf(Object obj)
	{
		if (optionalField == null || !optional.test(cast(obj)))
			return null;
		return optionalField.apply(cast(obj)).isEnabled();
	}

	AbstractCommand setter(Object obj, JsonElement value)
	{
		return set.apply(cast(obj), value);
	}

	AbstractCommand enabler(Object obj, boolean enabled)
	{
		if (optionalField == null || !optional.test(cast(obj)))
			return null;
		return optionalField.apply(cast(obj)).enabler(enabled);
	}

	/** The value two objects share, which is null where they differ, and is that of each element of an array. */
	private static JsonElement shared(JsonElement a, JsonElement b)
	{
		if (a == null || b == null)
			return null;
		if (a.isJsonArray() && b.isJsonArray() && a.getAsJsonArray().size() == b.getAsJsonArray().size()) {
			JsonArray values = new JsonArray();
			for (int i = 0; i < a.getAsJsonArray().size(); i++) {
				JsonElement value = shared(a.getAsJsonArray().get(i), b.getAsJsonArray().get(i));
				values.add(value == null ? JsonNull.INSTANCE : value);
			}
			return values;
		}
		return a.equals(b) ? a : null;
	}

	/** Describes `fields` for another program, with the values `objs` share. */
	static JsonArray describe(List<? extends RemoteField<?>> fields, List<?> objs)
	{
		JsonArray described = new JsonArray();
		Object first = objs.get(0);
		for (RemoteField<?> property : fields) {
			JsonObject field = new JsonObject();
			field.addProperty("key", property.key);
			field.addProperty("kind", property.kind);
			field.addProperty("label", property.labelOf(first));
			for (var extra : property.extra.entrySet())
				field.add(extra.getKey(), extra.getValue());
			JsonArray options = property.optionsOf(first);
			if (options != null)
				field.add("options", options);

			JsonElement value = property.valueOf(first);
			Boolean enabled = property.enabledOf(first);
			for (Object obj : objs) {
				value = shared(value, property.valueOf(obj));
				if (enabled != null && !enabled.equals(property.enabledOf(obj)))
					enabled = null;
			}
			if (value != null && !value.isJsonNull())
				field.add("value", value);
			if (property.enabledOf(first) != null) {
				if (enabled != null)
					field.addProperty("enabled", enabled);
				field.addProperty("optional", true);
			}
			described.add(field);
		}
		return described;
	}
}
