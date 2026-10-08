package game.map.editor;

import java.util.HashMap;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import game.map.MapObject;
import game.map.tree.MapObjectNode;
import game.map.tree.MapObjectTreeModel;

/**
 * A map's objects as JSON, for another program that shows them, and the objects by the IDs it gives them, which are
 * their {@link MapObject#uniqueID}s.
 */
public class MapObjectsJson
{
	private final HashMap<Integer, MapObject> objects = new HashMap<>();
	private final JsonArray categories = new JsonArray();

	/**
	 * The trees of models, colliders, zones, and markers. Each is a category: an object with an "id", a "name", the
	 * "rootId" of the group at its top, and "children", each an object with an "id", a "name", a "type" (as
	 * MapObjectType), whether it's a "group", and its own "children".
	 */
	public MapObjectsJson(List<MapObjectTreeModel<?>> trees, List<String> names)
	{
		for (int i = 0; i < trees.size(); i++) {
			JsonObject category = new JsonObject();
			category.addProperty("id", names.get(i).toLowerCase());
			category.addProperty("name", names.get(i));
			// the group at the top of the tree, which objects can be created in
			MapObject root = trees.get(i).getRoot().getUserObject();
			objects.put(root.uniqueID, root);
			category.addProperty("rootId", root.uniqueID);
			category.add("children", children(trees.get(i).getRoot()));
			categories.add(category);
		}
	}

	public JsonArray getCategories()
	{
		return categories;
	}

	/** The object with the given ID, or null if there's none. */
	public MapObject get(int id)
	{
		return objects.get(id);
	}

	private JsonArray children(MapObjectNode<?> node)
	{
		JsonArray children = new JsonArray();
		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<?> child = node.getChildAt(i);
			MapObject obj = child.getUserObject();
			objects.put(obj.uniqueID, obj);

			JsonObject json = new JsonObject();
			json.addProperty("id", obj.uniqueID);
			json.addProperty("name", obj.getName());
			json.addProperty("type", obj.getObjectType().name());
			json.addProperty("group", obj.allowsChildren());
			if (child.getChildCount() > 0)
				json.add("children", children(child));
			children.add(json);
		}
		return children;
	}
}
