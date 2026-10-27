package game.map.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;

import common.Vector3f;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.MapObject.SetObjectName;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.render.PreviewGeneratorPrimitive;
import game.map.editor.render.PreviewOriginMode;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.GuiCommand;
import game.map.hit.Collider;
import game.map.hit.Zone;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.shape.Model;
import game.map.tree.MapObjectNode;

/**
 * The actions another program can run on the map's objects, such as from a menu in its list of them: renaming,
 * copying, the Create and Modify Selected menus of each type of object, and those of a group in the object trees. It's
 * sent the type of object being selected, what of them is selected (the selection mode), whether the selection can
 * be edited, and whether anything has been copied, as an "objectState" message, whenever they change. Each action
 * checks the selection allows it before running.
 */
public class RemoteObjectActions
{
	private final MapEditor editor;
	private String stateSent = null;

	public RemoteObjectActions(MapEditor editor)
	{
		this.editor = editor;
	}

	private int selected(Class<? extends MapObject> type)
	{
		return editor.selectionManager.getSelectedObjects(type).size();
	}

	private boolean modifying()
	{
		return editor.getEditorMode() == EditorMode.Modify
			&& editor.selectionManager.getSelectionMode() == SelectionMode.OBJECT;
	}

	/** Each action that acts on the selection, with whether the selection allows it. */
	private LinkedHashMap<String, BooleanSupplier> selectionActions()
	{
		LinkedHashMap<String, BooleanSupplier> actions = new LinkedHashMap<>();
		BooleanSupplier any = () -> modifying() && !editor.selectionManager.getSelectedObjects().isEmpty();
		actions.put("copy", any);
		actions.put("paste", () -> modifying() && editor.hasCopiedObjects());
		actions.put("duplicate", any);
		actions.put("delete", any);
		actions.put("joinModels", () -> modifying() && selected(Model.class) > 1);
		actions.put("joinColliders", () -> modifying() && selected(Collider.class) > 1);
		actions.put("joinZones", () -> modifying() && selected(Zone.class) > 1);
		BooleanSupplier meshes = () -> modifying()
			&& selected(Model.class) + selected(Collider.class) + selected(Zone.class) > 0;
		actions.put("split", meshes);
		actions.put("separateVertices", meshes);
		actions.put("fuseVertices", meshes);
		actions.put("cleanupDegenerates", meshes);
		actions.put("invertNormals", meshes);
		actions.put("normalsToCamera", meshes);
		actions.put("generateUVs", () -> modifying() && selected(Model.class) > 0);
		actions.put("setRenderMode", () -> modifying() && selected(Model.class) > 0);
		actions.put("convertToZone", () -> modifying() && selected(Collider.class) > 0);
		actions.put("createBVH", () -> modifying() && selected(Collider.class) > 0);
		actions.put("convertToCollider", () -> modifying() && selected(Zone.class) > 0);
		actions.put("moveSelectedHere", any);
		return actions;
	}

	/** Sends the type of object being selected, and what the selection can do, if they changed. */
	public void sendState()
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "objectState");
		message.addProperty("objectType", editor.selectionManager.getObjectType().name());
		message.addProperty("selectionMode", editor.selectionManager.getSelectionMode().name());
		message.addProperty("modifying", modifying());
		message.addProperty("canPaste", editor.hasCopiedObjects());

		String state = message.toString();
		if (!state.equals(stateSent)) {
			stateSent = state;
			editor.remote().getSession().send(message);
		}
	}

	/** Shows the objects of a type, as Star Rod's tabs of them do, which also makes it the type selected. */
	public void showType(MapObjectType type)
	{
		SwingUtilities.invokeLater(() -> editor.gui.setObjectTab(type));
	}

	/**
	 * Runs an action, on the editor's thread or, for one that asks for more first, on Swing's. `target` is the
	 * object the action was asked of, such as a group to create an object in, or null for the selection.
	 */
	public void run(String action, MapObject target, JsonObject message)
	{
		LinkedHashMap<String, BooleanSupplier> actions = selectionActions();
		if (actions.containsKey(action) && !actions.get(action).getAsBoolean())
			return;

		@SuppressWarnings("unchecked")
		MapObjectNode<MapObject> node = (target != null) ? (MapObjectNode<MapObject>) target.getNode() : null;

		switch (action) {
			case "rename":
				if (target != null && message.has("name"))
					MapEditor.execute(() -> new SetObjectName(target, message.get("name").getAsString()));
				break;
			case "copy":
				editor.executeShortcut(EditorShortcut.COPY_OBJECTS);
				break;
			case "paste":
				if (node != null)
					editor.runEditorAction(() -> editor.action_PasteObjectsTo(node));
				else
					editor.executeShortcut(EditorShortcut.PASTE_OBJECTS);
				break;
			case "duplicate":
				editor.executeShortcut(EditorShortcut.DUPLICATE_SELECTED);
				break;
			case "delete":
				editor.executeShortcut(EditorShortcut.DELETE_SELECTED);
				break;
			case "invertNormals":
				editor.executeShortcut(EditorShortcut.FLIP_NORMALS);
				break;
			case "normalsToCamera":
				editor.executeShortcut(EditorShortcut.NORMALS_TO_CAMERA);
				break;
			case "joinModels":
				editor.submitGuiCommand(GuiCommand.JOIN_MODELS);
				break;
			case "joinColliders":
				editor.submitGuiCommand(GuiCommand.JOIN_COLLIDERS);
				break;
			case "joinZones":
				editor.submitGuiCommand(GuiCommand.JOIN_ZONES);
				break;
			case "split":
				editor.submitGuiCommand(switch (editor.selectionManager.getObjectType()) {
					case COLLIDER -> GuiCommand.SPLIT_COLLIDER;
					case ZONE -> GuiCommand.SPLIT_ZONE;
					default -> GuiCommand.SPLIT_MODEL;
				});
				break;
			case "separateVertices":
				editor.submitGuiCommand(GuiCommand.SEPARATE_VERTS);
				break;
			case "fuseVertices":
				editor.submitGuiCommand(GuiCommand.FUSE_VERTS);
				break;
			case "cleanupDegenerates":
				editor.submitGuiCommand(GuiCommand.CLEANUP_TRIS);
				break;
			case "setRenderMode":
				editor.submitGuiCommand(GuiCommand.SET_ALL_RENDER_MODE);
				break;
			case "convertToZone":
				editor.submitGuiCommand(GuiCommand.CONVERT_COLLIDER_TO_ZONE);
				break;
			case "createBVH":
				editor.submitGuiCommand(GuiCommand.CREATE_BVH);
				break;
			case "convertToCollider":
				editor.submitGuiCommand(GuiCommand.CONVERT_ZONE_TO_COLLIDER);
				break;
			case "generateUVs":
				SwingUtilities.invokeLater(() -> editor.gui.prompt_GenerateUV());
				break;
			case "moveSelectedHere":
				// to an index among the group's children, as they are before the move, or else after them
				if (node != null) {
					int index = message.has("index") ? message.get("index").getAsInt() : -1;
					editor.runEditorAction(() -> moveSelectedTo(node, index));
				}
				break;
			case "selectChildren":
				if (node != null)
					selectChildren(node);
				break;
			case "newGroup":
				if (node != null)
					editor.runEditorAction(() -> editor.action_CreateGroup(node));
				break;
			case "newPrimitive":
				if (node != null) {
					// where the menu was asked for, if it was in the view, or else the 3D cursor
					PreviewGeneratorPrimitive preview = editor.generatePrimitivePreview;
					if (atClick(message))
						preview.originMode = PreviewOriginMode.CLICK;
					else if (preview.originMode == PreviewOriginMode.CLICK)
						preview.originMode = PreviewOriginMode.CURSOR;
					SwingUtilities.invokeLater(() -> editor.gui.prompt_CreatePrimitiveObject(node));
				}
				break;
			case "newMarker":
				// of the type and with the name the other program asked for
				if (node != null && target instanceof Marker && message.has("markerType") && message.has("name")) {
					MarkerType type = MarkerType.valueOf(message.get("markerType").getAsString());
					String name = message.get("name").getAsString();
					Vector3f clicked = atClick(message) ? editor.getClickPoint() : null;
					editor.runEditorAction(() -> editor.action_CreateMarker(name, type, markerNode(node),
						(clicked != null) ? clicked : editor.getCursorPosition()));
				}
				break;
			case "fromTriangles":
				if (node != null)
					SwingUtilities.invokeLater(() -> editor.gui.prompt_GenerateFromTriangles(node));
				break;
			case "fromPaths":
				if (node != null)
					SwingUtilities.invokeLater(() -> editor.gui.prompt_GenerateFromPaths(node));
				break;
			case "import":
				if (node != null)
					SwingUtilities.invokeLater(() -> editor.gui.prompt_Import(node));
				break;
		}
	}

	/** Whether an action was asked for from the view's menu, to create objects where it was right clicked. */
	private static boolean atClick(JsonObject message)
	{
		return message.has("atClick") && message.get("atClick").getAsBoolean();
	}

	@SuppressWarnings("unchecked")
	private static MapObjectNode<Marker> markerNode(MapObjectNode<?> node)
	{
		return (MapObjectNode<Marker>) node;
	}

	@SuppressWarnings("unchecked")
	private void moveSelectedTo(MapObjectNode<?> node, int index)
	{
		switch (node.getUserObject().getObjectType()) {
			case MODEL -> editor.action_MoveSelectedModels((MapObjectNode<Model>) node, index);
			case COLLIDER -> editor.action_MoveSelectedColliders((MapObjectNode<Collider>) node, index);
			case ZONE -> editor.action_MoveSelectedZones((MapObjectNode<Zone>) node, index);
			case MARKER -> editor.action_MoveSelectedMarkers((MapObjectNode<Marker>) node, index);
			default -> {}
		}
	}

	/** Selects every object in a group, at any depth, in place of the selection. */
	private void selectChildren(MapObjectNode<?> node)
	{
		List<MapObject> children = new ArrayList<>();
		collect(node, children);
		editor.selectionManager.selectObjectsFromGUI(children, List.of(), false);
	}

	private static void collect(MapObjectNode<?> node, List<MapObject> into)
	{
		for (int i = 0; i < node.getChildCount(); i++) {
			MapObjectNode<?> child = node.getChildAt(i);
			into.add(child.getUserObject());
			collect(child, into);
		}
	}
}
