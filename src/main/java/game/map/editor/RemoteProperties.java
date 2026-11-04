package game.map.editor;

import static game.map.MapKey.*;
import static game.map.hit.Collider.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import common.Vector3f;
import common.commands.AbstractCommand;
import common.commands.CommandBatch;
import common.commands.EditableField;
import game.ProjectDatabase;
import game.entity.EntityInfo.EntityParam;
import game.entity.EntityInfo.EntityType;
import game.entity.EntityMenuGroup;
import game.map.MapKey;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.MapObject.ShapeType;
import game.map.MutablePoint.SetPosition;
import game.map.editor.render.RenderMode;
import game.map.editor.selection.SelectablePoint;
import game.map.editor.selection.SelectablePoint.SetPointPosition;
import game.map.editor.selection.SelectionManager.SelectionMode;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.ColliderInfoPanel;
import game.map.hit.CameraZoneData;
import game.map.hit.CameraZoneData.SetCameraFlag;
import game.map.hit.CameraZoneData.SetCameraPos;
import game.map.hit.CameraZoneData.SetCameraType;
import game.map.hit.Collider;
import game.map.hit.ControlType;
import game.map.hit.Zone;
import game.map.marker.CamTargetComponent;
import game.map.marker.LightComponent.SetFalloffType;
import game.map.marker.LightComponent.SetLightFalloff;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import game.map.marker.Marker.SetAngle;
import game.map.marker.Marker.SetType;
import game.map.marker.Marker.SetX;
import game.map.marker.Marker.SetY;
import game.map.marker.Marker.SetZ;
import game.map.marker.NpcComponent.MoveType;
import game.map.marker.NpcComponent.SetDetectPos;
import game.map.marker.NpcComponent.SetWanderPos;
import game.map.marker.PathData;
import game.map.marker.PathData.AddPathPoint;
import game.map.marker.PathData.DeletePathPoint;
import game.map.marker.PathData.SetPathPointName;
import game.map.marker.PathPoint;
import game.map.shading.FalloffType;
import game.map.shape.LightSet;
import game.map.shape.Model;
import game.map.shape.Model.SetMatrix;
import game.map.shape.Model.SetMatrixElement;
import game.map.shape.ModelReplaceType;
import game.map.shape.TransformMatrix;
import game.sprite.SpriteLoader;
import game.sprite.SpriteLoader.SpriteMetadata;
import game.sprite.SpriteLoader.SpriteSet;

/**
 * The properties of the selected objects, which another program shows and edits in place of the side panel. It's
 * sent them as a "properties" message whenever they change, each with a key, a label, the kind of control that edits
 * it, and the value the selected objects share, or no value where they differ. Setting a property sets it on every
 * selected object, as one command. Long lists of names that properties choose from, such as items, are sent apart, as
 * a "propertyLists" message, whenever they change.
 *
 * The kinds of control are:
 * <ul>
 * <li>"heading", which starts a section, and has no value</li>
 * <li>"text", a string, which can suggest the names of a `list`</li>
 * <li>"number", which is an `integer` or not</li>
 * <li>"vector", numbers labelled by `components`, which is set as [index, number]</li>
 * <li>"hex", a number shown in hexadecimal as `digits` digits</li>
 * <li>"color", an RGB color as a number</li>
 * <li>"checkbox", a boolean</li>
 * <li>"choice", the index of one of `options`, or of the names of a `list`, which can be in `groups`</li>
 * <li>"points", a list of [x, y, z], which is set as {index, axis, value}, or {index, remove: true}</li>
 * <li>"button", which runs an action when it's set, and has no value</li>
 * <li>"info", a string that can't be edited</li>
 * </ul>
 * A property that can be left out of what the object saves has an `enabled` flag as well, which is set apart.
 */
public class RemoteProperties
{
	private final MapEditor editor;
	private final HashMap<MapObjectType, List<? extends RemoteField<?>>> properties = new HashMap<>();
	private final List<RemoteField<PathPoint>> pathPointProperties = pathPointProperties();
	private final List<SpriteMetadata> sprites;
	private String sent = null;
	private String listsSent = null;
	private int frame = 0;

	public RemoteProperties(MapEditor editor)
	{
		this.editor = editor;
		sprites = new ArrayList<>(SpriteLoader.getValidSprites(SpriteSet.Npc));
		properties.put(MapObjectType.COLLIDER, colliderProperties());
		properties.put(MapObjectType.ZONE, zoneProperties());
		properties.put(MapObjectType.MODEL, modelProperties());
		properties.put(MapObjectType.MARKER, markerProperties());
	}

	// ---- kinds of property, for the fields of objects

	private static <T extends MapObject> RemoteField<T> heading(String key, String label)
	{
		return new RemoteField<>(key, "heading", label);
	}

	private static <T extends MapObject> RemoteField<T> checkbox(String key, String label,
		Function<T, EditableField<Boolean>> field)
	{
		return new RemoteField<T>(key, "checkbox", label)
			.get(obj -> new JsonPrimitive(field.apply(obj).get()))
			.set((obj, value) -> field.apply(obj).mutator(value.getAsBoolean()));
	}

	private static <T extends MapObject> RemoteField<T> text(String key, String label,
		Function<T, EditableField<String>> field)
	{
		return new RemoteField<T>(key, "text", label)
			.get(obj -> new JsonPrimitive(field.apply(obj).get()))
			.set((obj, value) -> field.apply(obj).mutator(value.getAsString()));
	}

	private static <T extends MapObject> RemoteField<T> number(String key, String label,
		Function<T, EditableField<Float>> field)
	{
		return new RemoteField<T>(key, "number", label)
			.get(obj -> new JsonPrimitive(field.apply(obj).get()))
			.set((obj, value) -> field.apply(obj).mutator(value.getAsFloat()));
	}

	private static <T extends MapObject> RemoteField<T> integer(String key, String label,
		Function<T, EditableField<Integer>> field)
	{
		return new RemoteField<T>(key, "number", label)
			.extra("integer", new JsonPrimitive(true))
			.get(obj -> new JsonPrimitive(field.apply(obj).get()))
			.set((obj, value) -> field.apply(obj).mutator(value.getAsInt()));
	}

	private static <T extends MapObject> RemoteField<T> hex(String key, String label, int digits,
		Function<T, EditableField<Integer>> field)
	{
		return new RemoteField<T>(key, "hex", label)
			.extra("digits", new JsonPrimitive(digits))
			.get(obj -> new JsonPrimitive(field.apply(obj).get()))
			.set((obj, value) -> field.apply(obj).mutator((int) value.getAsLong()));
	}

	/** A choice of one of `values`, by their names. */
	private static <T extends MapObject, E> RemoteField<T> choice(String key, String label, E[] values,
		Function<T, EditableField<E>> field)
	{
		JsonArray options = names(Arrays.asList(values));
		return new RemoteField<T>(key, "choice", label)
			.options(obj -> options)
			.get(obj -> new JsonPrimitive(Arrays.asList(values).indexOf(field.apply(obj).get())))
			.set((obj, value) -> field.apply(obj).mutator(values[value.getAsInt()]));
	}

	/** A vector of numbers, labelled by `components`. */
	private static <T> RemoteField<T> vector(String key, String label, String[] components,
		Function<T, double[]> get, BiFunction<T, double[], AbstractCommand> set)
	{
		JsonArray labels = names(Arrays.asList(components));
		return new RemoteField<T>(key, "vector", label)
			.extra("components", labels)
			.get(obj -> {
				JsonArray values = new JsonArray();
				for (double v : get.apply(obj))
					values.add(v);
				return values;
			})
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				int index = change.get(0).getAsInt();
				double v = change.get(1).getAsDouble();
				double[] values = get.apply(obj);
				values[index] = v;
				return set.apply(obj, values);
			});
	}

	private static <T> RemoteField<T> button(String key, String label, Function<T, AbstractCommand> run)
	{
		return new RemoteField<T>(key, "button", label).single().set((obj, value) -> run.apply(obj));
	}

	private static <T> RemoteField<T> info(String key, Function<T, String> text)
	{
		return new RemoteField<T>(key, "info", "").get(obj -> new JsonPrimitive(text.apply(obj)));
	}

	/** A path's points, which can be edited and removed, and a button that adds one. */
	private static <T extends MapObject> List<RemoteField<T>> path(String key, String label, Function<T, PathData> path,
		Predicate<T> shown)
	{
		RemoteField<T> points = new RemoteField<T>(key, "points", label)
			.single()
			.shown(shown)
			.get(obj -> {
				JsonArray values = new JsonArray();
				for (PathPoint point : path.apply(obj).points) {
					JsonArray position = new JsonArray();
					position.add(point.getX());
					position.add(point.getY());
					position.add(point.getZ());
					values.add(position);
				}
				return values;
			})
			.set((obj, value) -> {
				JsonObject change = value.getAsJsonObject();
				PathData data = path.apply(obj);
				int index = change.get("index").getAsInt();
				if (index < 0 || index >= data.points.size())
					return null;
				PathPoint point = data.points.get(index);
				if (change.has("remove"))
					return new DeletePathPoint(point);
				int[] position = { point.getX(), point.getY(), point.getZ() };
				position[change.get("axis").getAsInt()] = Math.round(change.get("value").getAsFloat());
				return new SetPointPosition("Set Path Point Position", point, position[0], position[1], position[2]);
			});
		RemoteField<T> add = RemoteProperties.<T> button(key + "Add", "Add point", obj -> new AddPathPoint(path.apply(obj)))
			.shown(shown)
			.shown(obj -> path.apply(obj).points.size() < path.apply(obj).maxLength);
		return List.of(points, add);
	}

	private static JsonArray names(Iterable<?> values)
	{
		JsonArray names = new JsonArray();
		for (Object value : values)
			names.add(String.valueOf(value));
		return names;
	}

	private static JsonPrimitive json(double value)
	{
		return new JsonPrimitive(value);
	}

	// ---- colliders

	private static List<RemoteField<Collider>> colliderProperties()
	{
		JsonArray surfaces = new JsonArray();
		for (String surface : ProjectDatabase.ESurfaceTypes.getValues())
			surfaces.add(ColliderInfoPanel.makePrettyName(surface));

		return List.of(
			new RemoteField<Collider>("surface", "choice", "Surface")
				.options(obj -> surfaces)
				.get(obj -> new JsonPrimitive(obj.surface.get()))
				.set((obj, value) -> obj.surface.mutator(value.getAsInt())),
			colliderFlag("ignorePlayer", "Ignore player and partner", IGNORE_PLAYER_BIT),
			colliderFlag("ignoreNpcs", "Ignore NPCs and items", IGNORE_NPC_BIT),
			colliderFlag("ignoreShell", "Ignore Kooper's shell", IGNORE_SHELL_BIT),
			hex("flags", "Flags", 8, obj -> obj.flags));
	}

	/** One bit of a collider's flags, as a checkbox. */
	private static RemoteField<Collider> colliderFlag(String key, String label, int bit)
	{
		return new RemoteField<Collider>(key, "checkbox", label)
			.get(obj -> new JsonPrimitive((obj.flags.get() & bit) != 0))
			.set((obj, value) -> {
				int flags = obj.flags.get();
				return obj.flags.mutator(value.getAsBoolean() ? (flags | bit) : (flags & ~bit));
			});
	}

	// ---- zones, and the camera settings they and camera targets share

	private static List<RemoteField<Zone>> zoneProperties()
	{
		List<RemoteField<Zone>> list = new ArrayList<>();
		list.add(checkbox("hasCamera", "Controls the camera", obj -> obj.hasCameraData));
		list.addAll(cameraProperties("camera", obj -> obj.camData, obj -> obj.hasCameraData.get()));
		return list;
	}

	private static final String[] XYZ = { "X", "Y", "Z" };

	/** How many points a Path marker lists before it leaves them to be selected in the view instead. */
	private static final int MAX_LISTED_POINTS = 20;

	/** The settings of a camera, which are shown where `shown` holds. */
	private static <T extends MapObject> List<RemoteField<T>> cameraProperties(String key,
		Function<T, CameraZoneData> camera, Predicate<T> shown)
	{
		ControlType[] types = ControlType.values();
		List<RemoteField<T>> list = new ArrayList<>();
		list.add(new RemoteField<T>(key + "Type", "choice", "Type")
			.options(obj -> names(Arrays.asList(types)))
			.get(obj -> new JsonPrimitive(camera.apply(obj).getType().ordinal()))
			.set((obj, value) -> new SetCameraType(camera.apply(obj), types[value.getAsInt()])));
		list.add(new RemoteField<T>(key + "Flag", "checkbox", "")
			.label(obj -> camera.apply(obj).getType().flagName)
			.shown(obj -> camera.apply(obj).getType().usesFlag)
			.get(obj -> new JsonPrimitive(camera.apply(obj).getFlag()))
			.set((obj, value) -> new SetCameraFlag(camera.apply(obj), value.getAsBoolean())));
		list.add(number(key + "BoomLength", "Boom length", obj -> camera.apply(obj).boomLength));
		list.add(number(key + "BoomPitch", "Boom pitch", obj -> camera.apply(obj).boomPitch));
		list.add(number(key + "ViewPitch", "View pitch", obj -> camera.apply(obj).viewPitch));
		for (char letter : new char[] { 'A', 'B', 'C' }) {
			list.add(RemoteProperties.<T> vector(key + "Point" + letter, "Point " + letter, XYZ,
				obj -> {
					CameraZoneData data = camera.apply(obj);
					var point = (letter == 'A') ? data.posA : (letter == 'B') ? data.posB : data.posC;
					return new double[] { point.getX(), point.getY(), point.getZ() };
				},
				(obj, values) -> null)
				.set((obj, value) -> {
					JsonArray change = value.getAsJsonArray();
					return new SetCameraPos(camera.apply(obj), letter, change.get(0).getAsInt(), change.get(1).getAsFloat());
				})
				.shown(obj -> switch (camera.apply(obj).getType()) {
					case TYPE_3 -> false;
					case TYPE_2, TYPE_5 -> true;
					default -> letter != 'C';
				}));
		}
		for (RemoteField<T> property : list)
			property.shown(shown);
		return list;
	}

	// ---- models

	/** The scales of a texture's auxiliary coordinates, by name, with each one's shift. */
	private static final String[] AUX_SCALE_NAMES = {
			"1/32", "1/16", "1/8", "1/4", "1/2", "1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024" };
	private static final int[] AUX_SCALE_SHIFTS = { 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

	private static int auxScaleIndex(int shift)
	{
		for (int i = 0; i < AUX_SCALE_SHIFTS.length; i++) {
			if (AUX_SCALE_SHIFTS[i] == shift)
				return i;
		}
		return 5;
	}

	private static RemoteField<Model> auxScale(String key, String label, Function<Model, EditableField<Integer>> field)
	{
		return new RemoteField<Model>(key, "choice", label)
			.options(obj -> names(Arrays.asList(AUX_SCALE_NAMES)))
			.get(obj -> new JsonPrimitive(auxScaleIndex(field.apply(obj).get())))
			.set((obj, value) -> field.apply(obj).mutator(AUX_SCALE_SHIFTS[value.getAsInt()]));
	}

	private List<RemoteField<Model>> modelProperties()
	{
		ShapeType[] shapeTypes = { ShapeType.MODEL, ShapeType.GROUP, ShapeType.SPECIAL };
		Predicate<Model> leaf = obj -> obj.modelType.get() == ShapeType.MODEL;
		Predicate<Model> group = obj -> obj.modelType.get() != ShapeType.MODEL;
		Predicate<Model> aux = obj -> leaf.test(obj) && obj.hasAuxProperties.get();
		Predicate<Model> transform = obj -> group.test(obj) && obj.hasTransformMatrix.get();

		String[] panners = new String[17];
		panners[0] = "None";
		for (int i = 0; i < 16; i++)
			panners[i + 1] = String.valueOf(i);
		Integer[] presetPanners = new Integer[16];
		for (int i = 0; i < 16; i++)
			presetPanners[i] = i;

		List<RemoteField<Model>> list = new ArrayList<>();
		list.add(new RemoteField<Model>("type", "choice", "Type")
			.options(obj -> names(Arrays.asList(shapeTypes)))
			.shown(obj -> obj.modelType.get() != ShapeType.ROOT)
			.get(obj -> new JsonPrimitive(Arrays.asList(shapeTypes).indexOf(obj.modelType.get())))
			.set((obj, value) -> {
				ShapeType type = shapeTypes[value.getAsInt()];
				// a model can't have children
				if (type == ShapeType.MODEL && obj.getNode().getChildCount() != 0)
					return null;
				return obj.modelType.mutator(type);
			}));

		// models
		list.add(choice("renderMode", "Render mode", RenderMode.getEditorModes(), (Model obj) -> obj.renderMode)
			.shown(leaf));
		list.add(new RemoteField<Model>("panner", "choice", "Texture panner")
			.shown(leaf)
			.options(obj -> names(Arrays.asList(panners)))
			.get(obj -> new JsonPrimitive(obj.pannerID.get() + 1))
			.set((obj, value) -> obj.pannerID.mutator(value.getAsInt() - 1)));
		list.add(RemoteProperties.<Model> button("editPanner", "Edit panner", obj -> {
			var panner = editor.map.features.texPanners.get(obj.pannerID.get());
			SwingUtilities.invokeLater(() -> SwingGUI.instance().prompt_EditTexPanner(panner));
			return null;
		}).shown(leaf).shown(obj -> obj.pannerID.get() >= 0));
		list.add(checkbox("hasAux", "Special properties", (Model obj) -> obj.hasAuxProperties).shown(leaf));
		list.add(RemoteProperties.<Model> vector("auxOffset", "Aux offset", new String[] { "S", "T" },
			obj -> new double[] { obj.auxOffsetS.get() / 4.0, obj.auxOffsetT.get() / 4.0 },
			(obj, values) -> {
				CommandBatch batch = new CommandBatch("Set Aux Offset");
				batch.addCommand(obj.auxOffsetS.mutator((int) Math.round(values[0] * 4.0)));
				batch.addCommand(obj.auxOffsetT.mutator((int) Math.round(values[1] * 4.0)));
				return batch;
			}).shown(aux));
		list.add(auxScale("auxScaleS", "Aux scale S", obj -> obj.auxShiftS).shown(aux));
		list.add(auxScale("auxScaleT", "Aux scale T", obj -> obj.auxShiftT).shown(aux));
		list.add(choice("presetPanner", "Preset panner", presetPanners, (Model obj) -> obj.defaultPannerID).shown(aux));
		list.add(choice("replaceWith", "Replace with", ModelReplaceType.values(), (Model obj) -> obj.replaceWith)
			.shown(aux));

		// groups
		list.add(new RemoteField<Model>("lights", "choice", "Lights")
			.shown(group)
			.options(obj -> names(editor.map.lightSets))
			.get(obj -> new JsonPrimitive(editor.map.lightSets.indexOf(obj.lights.get())))
			.set((obj, value) -> {
				LightSet lights = editor.map.lightSets.get(value.getAsInt());
				return obj.lights.mutator(lights);
			}));
		list.add(checkbox("hasMesh", "Has mesh", (Model obj) -> obj.hasMesh).shown(group));
		list.add(checkbox("hasProp60", "Property 60", (Model obj) -> obj.hasProp60).shown(group));
		list.add(hex("prop60a", "Property 60 A", 2, (Model obj) -> obj.prop60a)
			.shown(group).shown(obj -> obj.hasProp60.get()));
		list.add(hex("prop60b", "Property 60 B", 2, (Model obj) -> obj.prop60b)
			.shown(group).shown(obj -> obj.hasProp60.get()));

		// a group's transform
		list.add(RemoteProperties.<Model> heading("transformHeading", "Transform").shown(group));
		list.add(checkbox("hasTransform", "Transform matrix", (Model obj) -> obj.hasTransformMatrix).shown(group));
		list.add(RemoteProperties.<Model> vector("translation", "Translation", XYZ,
			obj -> new double[] {
					obj.localTransformMatrix.get(0, 3),
					obj.localTransformMatrix.get(1, 3),
					obj.localTransformMatrix.get(2, 3) },
			(obj, values) -> null)
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				return new SetMatrixElement(obj, change.get(0).getAsInt(), 3, change.get(1).getAsDouble());
			})
			.shown(transform));
		for (int row = 0; row < 3; row++) {
			final int r = row;
			list.add(RemoteProperties.<Model> vector("matrixRow" + row, "Row " + (row + 1), XYZ,
				obj -> new double[] {
						obj.localTransformMatrix.get(r, 0),
						obj.localTransformMatrix.get(r, 1),
						obj.localTransformMatrix.get(r, 2) },
				(obj, values) -> null)
				.set((obj, value) -> {
					JsonArray change = value.getAsJsonArray();
					return new SetMatrixElement(obj, r, change.get(0).getAsInt(), change.get(1).getAsDouble());
				})
				.shown(transform));
		}
		// rotation and scale, which change the matrix once baked, and preview it before then
		list.add(RemoteProperties.<Model> vector("rotation", "Rotation", XYZ,
			obj -> obj.localTransformMatrix.txRot.clone(),
			(obj, values) -> previewTransform(obj, values, obj.localTransformMatrix.txRot))
			.single().shown(transform));
		list.add(RemoteProperties.<Model> vector("scale", "Scale", XYZ,
			obj -> obj.localTransformMatrix.txScale.clone(),
			(obj, values) -> previewTransform(obj, values, obj.localTransformMatrix.txScale))
			.single().shown(transform));
		list.add(new RemoteField<Model>("previewTransform", "checkbox", "Preview rotation and scale")
			.single().shown(transform)
			.get(obj -> new JsonPrimitive(obj.localTransformMatrix.usePreview))
			.set((obj, value) -> {
				obj.localTransformMatrix.usePreview = value.getAsBoolean();
				obj.updateTransformHierarchy();
				return null;
			}));
		list.add(RemoteProperties.<Model> button("bakeTransform", "Bake transform", obj -> {
			TransformMatrix matrix = obj.localTransformMatrix;
			return new SetMatrix(obj, matrix.getBakedMatrix(), matrix.baked);
		}).shown(transform));
		list.add(RemoteProperties.<Model> button("decomposeTransform", "Decompose", obj -> {
			obj.localTransformMatrix.decompose();
			obj.localTransformMatrix.baked = true;
			return null;
		}).shown(transform));
		return list;
	}

	/** Sets a transform's rotation or scale, which isn't undoable until it's baked into the matrix. */
	private static AbstractCommand previewTransform(Model obj, double[] values, double[] into)
	{
		System.arraycopy(values, 0, into, 0, 3);
		obj.localTransformMatrix.baked = false;
		if (obj.localTransformMatrix.usePreview)
			obj.updateTransformHierarchy();
		return null;
	}

	// ---- markers

	private static Predicate<Marker> isType(MarkerType... types)
	{
		List<MarkerType> list = Arrays.asList(types);
		return obj -> list.contains(obj.getType());
	}

	private List<RemoteField<Marker>> markerProperties()
	{
		MarkerType[] markerTypes = Arrays.stream(MarkerType.values())
			.filter(type -> type != MarkerType.Root && type != MarkerType.Group)
			.toArray(MarkerType[]::new);
		Predicate<Marker> leaf = obj -> obj.getType() != MarkerType.Root && obj.getType() != MarkerType.Group;

		List<RemoteField<Marker>> list = new ArrayList<>();
		list.add(new RemoteField<Marker>("type", "choice", "Type")
			.shown(leaf)
			.options(obj -> names(Arrays.asList(markerTypes)))
			.get(obj -> new JsonPrimitive(Arrays.asList(markerTypes).indexOf(obj.getType())))
			.set((obj, value) -> new SetType(obj, markerTypes[value.getAsInt()])));
		list.add(new RemoteField<Marker>("position", "vector", "Position")
			.shown(leaf)
			.extra("components", names(Arrays.asList(XYZ)))
			.extra("integer", new JsonPrimitive(true))
			.get(obj -> {
				JsonArray values = new JsonArray();
				values.add(obj.position.getX());
				values.add(obj.position.getY());
				values.add(obj.position.getZ());
				return values;
			})
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				int v = Math.round(change.get(1).getAsFloat());
				return switch (change.get(0).getAsInt()) {
					case 0 -> new SetX(obj, v);
					case 1 -> new SetY(obj, v);
					default -> new SetZ(obj, v);
				};
			}));
		list.add(new RemoteField<Marker>("angle", "number", "Angle")
			.shown(leaf)
			.get(obj -> json(obj.yaw.getAngle()))
			.set((obj, value) -> new SetAngle(obj, value.getAsFloat())));
		list.add(RemoteProperties.<Marker> info("aboveGround", obj -> {
			int height = (int) obj.heightAboveGround;
			if (obj.heightAboveGround == Float.MAX_VALUE)
				return "No ground below";
			return (height == 1) ? "1 unit above the ground" : height + " units above the ground";
		}).shown(leaf).single());

		// volumes
		list.add(number("radius", "Radius", (Marker obj) -> obj.volumeComponent.radius)
			.shown(isType(MarkerType.Sphere, MarkerType.Cylinder)));
		list.add(number("height", "Height", (Marker obj) -> obj.volumeComponent.height)
			.shown(isType(MarkerType.Cylinder)));

		// paths
		Predicate<Marker> isPath = isType(MarkerType.Path);
		list.add(checkbox("showInterp", "Show spline interpolation", (Marker obj) -> obj.pathComponent.showInterp)
			.shown(isPath)
			.shown(obj -> obj.pathComponent.path.isLinear()));
		list.add(RemoteProperties.<Marker> info("unnamedPoints",
			obj -> "Name every point, so code can tell which points each one leads to.")
			.shown(isPath)
			.shown(obj -> !obj.pathComponent.path.isLinear() && !obj.pathComponent.path.isNamed()));
		// Path mode adds points, so only the list of them, not its add button
		RemoteField<Marker> points = path("path", "Points", obj -> obj.pathComponent.path, isPath).get(0);
		list.add(points.shown(obj -> obj.pathComponent.path.points.size() <= MAX_LISTED_POINTS));

		// push block grids
		Predicate<Marker> grid = isType(MarkerType.BlockGrid);
		list.add(integer("gridIndex", "Grid ID", (Marker obj) -> obj.gridComponent.gridIndex).shown(grid));
		list.add(RemoteProperties.<Marker> vector("gridSize", "Grid size", new String[] { "X", "Z" },
			obj -> new double[] { obj.gridComponent.gridSizeX.get(), obj.gridComponent.gridSizeZ.get() },
			(obj, values) -> null)
			.extra("integer", new JsonPrimitive(true))
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				int v = change.get(1).getAsInt();
				return (change.get(0).getAsInt() == 0)
					? obj.gridComponent.gridSizeX.mutator(v)
					: obj.gridComponent.gridSizeZ.mutator(v);
			})
			.shown(grid));
		list.add(RemoteProperties.<Marker> button("gridEditing", "", obj -> {
			EditableField<Boolean> field = obj.gridComponent.showEditHandles;
			return field.mutator(!field.get());
		}).label(obj -> (obj.gridComponent.showEditHandles.get() ? "Stop" : "Start") + " editing blocks").shown(grid));

		list.addAll(npcProperties());
		list.addAll(entityProperties());

		// lights
		Predicate<Marker> light = isType(MarkerType.Light);
		list.add(new RemoteField<Marker>("lightColor", "color", "Color")
			.shown(light)
			.get(obj -> new JsonPrimitive(obj.lightComponent.color.get() & 0xFFFFFF))
			.set((obj, value) -> obj.lightComponent.color.mutator(value.getAsInt() & 0xFFFFFF)));
		list.add(new RemoteField<Marker>("lightRadius", "number", "Radius")
			.shown(light)
			.get(obj -> json(obj.lightComponent.falloffDist))
			.set((obj, value) -> new SetLightFalloff(obj.lightComponent, value.getAsDouble())));
		FalloffType[] falloffs = FalloffType.values();
		list.add(new RemoteField<Marker>("lightFalloff", "choice", "Falloff")
			.shown(light)
			.options(obj -> names(Arrays.asList(falloffs)))
			.get(obj -> new JsonPrimitive(obj.lightComponent.falloffType.ordinal()))
			.set((obj, value) -> new SetFalloffType(obj.lightComponent, falloffs[value.getAsInt()])));
		list.add(checkbox("lightEnabled", "Initially on", (Marker obj) -> obj.lightComponent.enabled).shown(light));
		list.addAll(spriteShadingProperties(light));

		list.addAll(cameraTargetProperties());
		return list;
	}

	/**
	 * The map's sprite shading, which its lights need in order to show. It belongs to the map rather than a light, so
	 * it's only shown while one light is selected, which sets it once.
	 */
	private List<RemoteField<Marker>> spriteShadingProperties(Predicate<Marker> light)
	{
		Predicate<Marker> shaded = obj -> editor.map.features.hasSpriteShading.get();
		return List.of(
			RemoteProperties.<Marker> heading("shadingHeading", "Sprite shading").single().shown(light),
			checkbox("hasShading", "Enable", (Marker obj) -> editor.map.features.hasSpriteShading)
				.single().shown(light),
			new RemoteField<Marker>("shadingColor", "color", "Base color")
				.single().shown(light).shown(shaded)
				.get(obj -> new JsonPrimitive(editor.map.features.shadingBaseColor.get() & 0xFFFFFF))
				.set((obj, value) -> editor.map.features.shadingBaseColor.mutator(value.getAsInt() & 0xFFFFFF)),
			integer("shadingOffset", "Edge offset", (Marker obj) -> editor.map.features.shadingOffset)
				.single().shown(light).shown(shaded)
				.set((obj, value) -> editor.map.features.shadingOffset.mutator(value.getAsInt() & 0xFF)));
	}

	private List<RemoteField<Marker>> npcProperties()
	{
		Predicate<Marker> npc = isType(MarkerType.NPC);
		Predicate<Marker> hasSprite = obj -> obj.npcComponent.previewSprite != null;
		Predicate<Marker> wander = obj -> obj.npcComponent.moveType.get() == MoveType.Wander;
		Predicate<Marker> patrol = obj -> obj.npcComponent.moveType.get() == MoveType.Patrol;
		String[] shapes = { "Circle", "Box" };

		List<RemoteField<Marker>> list = new ArrayList<>();
		list.add(new RemoteField<Marker>("sprite", "choice", "Sprite")
			.extra("list", "sprites")
			.get(obj -> {
				int id = obj.npcComponent.spriteID.get();
				for (int i = 0; i < sprites.size(); i++) {
					if (sprites.get(i).id == id)
						return new JsonPrimitive(i);
				}
				return JsonNull.INSTANCE;
			})
			.set((obj, value) -> obj.npcComponent.spriteID.mutator(sprites.get(value.getAsInt()).id)));
		list.add(new RemoteField<Marker>("palette", "choice", "Palette")
			.shown(hasSprite)
			.options(obj -> names(obj.npcComponent.previewSprite.palettes))
			.get(obj -> new JsonPrimitive(obj.npcComponent.paletteID.get()))
			.set((obj, value) -> obj.npcComponent.paletteID.mutator(value.getAsInt())));
		list.add(new RemoteField<Marker>("animation", "choice", "Animation")
			.shown(hasSprite)
			.options(obj -> names(obj.npcComponent.previewSprite.animations))
			.get(obj -> new JsonPrimitive(obj.npcComponent.animIndex.get()))
			.set((obj, value) -> obj.npcComponent.animIndex.mutator(value.getAsInt())));

		list.add(heading("movementHeading", "Movement"));
		list.add(choice("moveType", "Type", MoveType.values(), (Marker obj) -> obj.npcComponent.moveType));
		list.add(checkbox("flying", "Flying", (Marker obj) -> obj.npcComponent.flying));
		list.add(checkbox("overrideSpeed", "Override speed", (Marker obj) -> obj.npcComponent.overrideMovementSpeed));
		list.add(number("speed", "Speed", (Marker obj) -> obj.npcComponent.movementSpeedOverride)
			.shown(obj -> obj.npcComponent.overrideMovementSpeed.get()));

		list.add(heading("detectHeading", "Detection"));
		list.add(RemoteProperties.<Marker> vector("detectCenter", "Center", XYZ,
			obj -> new double[] {
					obj.npcComponent.detectCenter.getX(),
					obj.npcComponent.detectCenter.getY(),
					obj.npcComponent.detectCenter.getZ() },
			(obj, values) -> null)
			.extra("integer", new JsonPrimitive(true))
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				return new SetDetectPos(obj, change.get(0).getAsInt(), change.get(1).getAsInt());
			}));
		list.add(new RemoteField<Marker>("detectShape", "choice", "Shape")
			.options(obj -> names(Arrays.asList(shapes)))
			.get(obj -> new JsonPrimitive(obj.npcComponent.useDetectCircle.get() ? 0 : 1))
			.set((obj, value) -> obj.npcComponent.useDetectCircle.mutator(value.getAsInt() == 0)));
		list.add(integer("detectRadius", "Radius", (Marker obj) -> obj.npcComponent.detectRadius)
			.shown(obj -> obj.npcComponent.useDetectCircle.get()));
		list.add(RemoteProperties.<Marker> vector("detectSize", "Size", new String[] { "X", "Z" },
			obj -> new double[] { obj.npcComponent.detectSizeX.get(), obj.npcComponent.detectSizeZ.get() },
			(obj, values) -> null)
			.extra("integer", new JsonPrimitive(true))
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				int v = change.get(1).getAsInt();
				return (change.get(0).getAsInt() == 0)
					? obj.npcComponent.detectSizeX.mutator(v)
					: obj.npcComponent.detectSizeZ.mutator(v);
			})
			.shown(obj -> !obj.npcComponent.useDetectCircle.get()));

		list.add(RemoteProperties.<Marker> heading("wanderHeading", "Wandering").shown(wander));
		list.add(RemoteProperties.<Marker> vector("wanderCenter", "Center", XYZ,
			obj -> new double[] {
					obj.npcComponent.wanderCenter.getX(),
					obj.npcComponent.wanderCenter.getY(),
					obj.npcComponent.wanderCenter.getZ() },
			(obj, values) -> null)
			.extra("integer", new JsonPrimitive(true))
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				return new SetWanderPos(obj, change.get(0).getAsInt(), change.get(1).getAsInt());
			})
			.shown(wander));
		list.add(new RemoteField<Marker>("wanderShape", "choice", "Shape")
			.shown(wander)
			.options(obj -> names(Arrays.asList(shapes)))
			.get(obj -> new JsonPrimitive(obj.npcComponent.useWanderCircle.get() ? 0 : 1))
			.set((obj, value) -> obj.npcComponent.useWanderCircle.mutator(value.getAsInt() == 0)));
		list.add(integer("wanderRadius", "Radius", (Marker obj) -> obj.npcComponent.wanderRadius)
			.shown(wander).shown(obj -> obj.npcComponent.useWanderCircle.get()));
		list.add(RemoteProperties.<Marker> vector("wanderSize", "Size", new String[] { "X", "Z" },
			obj -> new double[] { obj.npcComponent.wanderSizeX.get(), obj.npcComponent.wanderSizeZ.get() },
			(obj, values) -> null)
			.extra("integer", new JsonPrimitive(true))
			.set((obj, value) -> {
				JsonArray change = value.getAsJsonArray();
				int v = change.get(1).getAsInt();
				return (change.get(0).getAsInt() == 0)
					? obj.npcComponent.wanderSizeX.mutator(v)
					: obj.npcComponent.wanderSizeZ.mutator(v);
			})
			.shown(wander).shown(obj -> !obj.npcComponent.useWanderCircle.get()));

		list.add(RemoteProperties.<Marker> heading("patrolHeading", "Patrol").shown(patrol));
		list.addAll(path("patrol", "Points", obj -> obj.npcComponent.patrolPath, patrol));

		for (RemoteField<Marker> property : list)
			property.shown(npc);
		return list;
	}

	private List<RemoteField<Marker>> entityProperties()
	{
		Predicate<Marker> entity = isType(MarkerType.Entity);
		EntityType[] types = EntityType.values();
		JsonArray groups = new JsonArray();
		for (EntityType type : types)
			groups.add(type.menuGroup == EntityMenuGroup.Hidden ? "Other" : type.menuGroup.toString());

		List<RemoteField<Marker>> list = new ArrayList<>();
		list.add(choice("entityType", "Entity", types, (Marker obj) -> obj.entityComponent.type)
			.extra("groups", groups));
		list.add(entityText(ATTR_NTT_ITEM, "item", "Item", obj -> obj.entityComponent.itemName, "items"));
		list.add(entityText(ATTR_NTT_GAME_FLAG, "gameFlag", "Game flag", obj -> obj.entityComponent.gameFlagName,
			"gameFlags"));
		list.add(entityText(ATTR_NTT_AREA_FLAG, "areaFlag", "Area flag", obj -> obj.entityComponent.areaFlagName,
			"areaFlags"));
		list.add(entityText(ATTR_NTT_MAP_VAR, "mapVar", "Map var", obj -> obj.entityComponent.mapVarName, "mapVars"));
		list.add(entityText(ATTR_NTT_SCRIPT, "script", "Script", obj -> obj.entityComponent.scriptName, null));
		list.add(entityInt(ATTR_NTT_INDEX, "index", "Index", obj -> obj.entityComponent.index));
		list.add(entityInt(ATTR_NTT_STYLE, "style", "Style", obj -> obj.entityComponent.style));
		list.add(entityText(ATTR_NTT_MODEL, "model", "Model", obj -> obj.entityComponent.modelName, "models"));
		list.add(entityText(ATTR_NTT_COLLIDER, "collider", "Collider", obj -> obj.entityComponent.colliderName,
			"colliders"));
		list.add(entityText(ATTR_NTT_TARGET, "target", "Target", obj -> obj.entityComponent.targetName, "positions"));
		list.add(entityText(ATTR_NTT_ENTRY, "entry", "Entry", obj -> obj.entityComponent.entryName, null));
		list.add(entityText(ATTR_NTT_PATHS, "paths", "Paths", obj -> obj.entityComponent.pathsName, null));
		list.add(entityInt(ATTR_NTT_ANGLE, "entityAngle", "Angle", obj -> obj.entityComponent.angle));
		list.add(entityInt(ATTR_NTT_LAUNCH_DIST, "launchDist", "Launch distance",
			obj -> obj.entityComponent.launchDist));
		list.add(entityText(ATTR_NTT_SPAWN_MODE, "spawnMode", "Spawn mode", obj -> obj.entityComponent.spawnMode,
			"spawnModes"));

		for (RemoteField<Marker> property : list)
			property.shown(entity);
		return list;
	}

	private static EntityParam entityParam(Marker obj, MapKey key)
	{
		return obj.entityComponent.type.get().getParam(key);
	}

	/** A parameter of an entity, shown for the types that have it, which can be left out unless it's required. */
	private static RemoteField<Marker> entityParam(RemoteField<Marker> property, MapKey key,
		Function<Marker, EditableField<?>> field)
	{
		return property
			.shown(obj -> entityParam(obj, key) != null)
			.optional(field, obj -> !entityParam(obj, key).required);
	}

	private static RemoteField<Marker> entityText(MapKey key, String name, String label,
		Function<Marker, EditableField<String>> field, String suggestions)
	{
		RemoteField<Marker> property = text(name, label, field);
		if (suggestions != null)
			property.extra("list", suggestions);
		return entityParam(property, key, field::apply);
	}

	private static RemoteField<Marker> entityInt(MapKey key, String name, String label,
		Function<Marker, EditableField<Integer>> field)
	{
		return entityParam(integer(name, label, field), key, field::apply);
	}

	private List<RemoteField<Marker>> cameraTargetProperties()
	{
		Predicate<Marker> target = isType(MarkerType.CamTarget);
		Predicate<Marker> useZone = obj -> obj.cameraComponent.useZone.get();
		Function<Marker, CamTargetComponent> camera = obj -> obj.cameraComponent;

		List<RemoteField<Marker>> list = new ArrayList<>();
		list.add(checkbox("generatePan", "Generate pan motion", (Marker obj) -> camera.apply(obj).generatePan));
		list.add(number("moveSpeed", "Pan speed", (Marker obj) -> camera.apply(obj).moveSpeed)
			.shown(obj -> camera.apply(obj).generatePan.get()));
		list.add(checkbox("useZone", "Use the settings of the zone below", (Marker obj) -> camera.apply(obj).useZone));
		list.add(RemoteProperties.<Marker> info("sampleZone", obj -> {
			CamTargetComponent component = camera.apply(obj);
			return (component.sampleHitPos == null) ? "No zone below" : "Found " + component.sampleZone.getName();
		}).single().shown(useZone));
		list.add(checkbox("overrideDist", "Override boom length", (Marker obj) -> camera.apply(obj).overrideDist)
			.shown(useZone));
		list.add(number("targetBoomLength", "Boom length", (Marker obj) -> camera.apply(obj).boomLength)
			.shown(useZone).shown(obj -> camera.apply(obj).overrideDist.get()));
		list.add(checkbox("overrideAngles", "Override pitch", (Marker obj) -> camera.apply(obj).overrideAngles)
			.shown(useZone));
		list.add(number("targetBoomPitch", "Boom pitch", (Marker obj) -> camera.apply(obj).boomPitch)
			.shown(useZone).shown(obj -> camera.apply(obj).overrideAngles.get()));
		list.add(number("targetViewPitch", "View pitch", (Marker obj) -> camera.apply(obj).viewPitch)
			.shown(useZone).shown(obj -> camera.apply(obj).overrideAngles.get()));
		list.addAll(cameraProperties("targetCamera", obj -> camera.apply(obj).controlData, useZone.negate()));

		list.add(RemoteProperties.<Marker> button("useCurrentView", "Use current view", obj -> {
			editor.setCameraParams(camera.apply(obj).controlData, obj.position.getVector());
			return null;
		}).shown(useZone.negate()));
		list.add(RemoteProperties.<Marker> button("lookAtTarget", "Look at target", obj -> {
			editor.cameraLookAt(obj.position.getVector());
			return null;
		}).shown(useZone.negate()));
		list.add(RemoteProperties.<Marker> button("togglePreview", "Toggle preview", obj -> {
			editor.toggleCameraTargetMarker(obj);
			return null;
		}));
		list.add(RemoteProperties.<Marker> button("centerMarker", "Center marker", obj -> {
			CamTargetComponent component = camera.apply(obj);
			float boomLength = component.boomLength.get();
			if (component.useZone.get() && !component.overrideDist.get())
				boomLength = component.controlData.boomLength.get();
			Vector3f position = editor.getPointAhead(boomLength);
			return new SetPosition(obj.position, position);
		}));

		for (RemoteField<Marker> property : list)
			property.shown(target);
		return list;
	}

	// ---- path points

	private List<RemoteField<PathPoint>> pathPointProperties()
	{
		Predicate<PathPoint> connectable = point -> point.path.isConnectable();

		List<RemoteField<PathPoint>> list = new ArrayList<>();
		list.add(new RemoteField<PathPoint>("name", "text", "Name")
			.single()
			.shown(connectable)
			.get(point -> new JsonPrimitive(point.getName() == null ? "" : point.getName()))
			.set((point, value) -> new SetPathPointName(point, value.getAsString().trim())));
		list.add(RemoteProperties.<PathPoint> vector("position", "Position", XYZ,
			point -> new double[] { point.getX(), point.getY(), point.getZ() },
			(point, values) -> new SetPointPosition("Set Path Point Position", point,
				(int) Math.round(values[0]), (int) Math.round(values[1]), (int) Math.round(values[2])))
			.extra("integer", new JsonPrimitive(true)));
		return list;
	}

	/** The selected points, if they're all points of paths, or else none. */
	private List<PathPoint> selectedPathPoints()
	{
		List<PathPoint> points = new ArrayList<>();
		for (SelectablePoint point : editor.selectionManager.pointSelection.selectableList) {
			if (!(point instanceof PathPoint pathPoint))
				return List.of();
			points.add(pathPoint);
		}
		return points;
	}

	private static String pointLabel(PathPoint point)
	{
		return (point.getName() != null) ? point.getName() : "point " + (point.getListIndex() + 1);
	}

	// ---- sending and setting

	/** What's selected: the selected objects, or in point selection mode, the selected points of paths. */
	private List<?> selection()
	{
		return switch (editor.selectionManager.getSelectionMode()) {
			case OBJECT -> editor.selectionManager.getSelectedObjects();
			case POINT -> selectedPathPoints();
			default -> List.of();
		};
	}

	/** The properties every one of the selected objects or points shows. */
	private List<RemoteField<?>> properties(List<?> objs)
	{
		List<RemoteField<?>> shown = new ArrayList<>();
		if (objs.isEmpty())
			return shown;
		List<? extends RemoteField<?>> all = (objs.get(0) instanceof MapObject first)
			? properties.getOrDefault(first.getObjectType(), List.of())
			: pathPointProperties;
		for (RemoteField<?> property : all) {
			if (property.single && objs.size() > 1)
				continue;
			boolean everyObject = true;
			for (Object obj : objs) {
				if (!sameKind(obj, objs.get(0)) || !property.isShown(obj)) {
					everyObject = false;
					break;
				}
			}
			if (everyObject)
				shown.add(property);
		}
		return shown;
	}

	/** Sends the properties of the selected objects, and the lists they choose from, if they changed. */
	public void send()
	{
		if (frame++ % 30 == 0)
			sendLists();

		List<?> objs = selection();
		List<RemoteField<?>> shown = properties(objs);

		JsonObject message = new JsonObject();
		message.addProperty("type", "properties");
		if (!shown.isEmpty()) {
			if (objs.get(0) instanceof PathPoint point) {
				if (objs.size() == 1) {
					message.addProperty("title", pointLabel(point));
					message.addProperty("subtitle", "Path point");
				}
				else {
					message.addProperty("title", objs.size() + " path points");
				}
			}
			else {
				MapObject first = (MapObject) objs.get(0);
				String typeName = first.getObjectType().toString();
				if (objs.size() == 1) {
					message.addProperty("title", first.getName());
					message.addProperty("subtitle", typeName);
				}
				else {
					message.addProperty("title", objs.size() + " " + typeName.toLowerCase() + "s");
				}
			}

			JsonArray fields = RemoteField.describe(shown, objs);
			message.add("properties", fields);
		}

		String text = message.toString();
		if (!text.equals(sent)) {
			sent = text;
			editor.remote().getSession().send(message);
		}
	}

	/** Whether two selected things are edited with the same properties: objects of one type, or points. */
	private static boolean sameKind(Object a, Object b)
	{
		if (a instanceof MapObject objA && b instanceof MapObject objB)
			return objA.getObjectType() == objB.getObjectType();
		return a.getClass() == b.getClass();
	}

	/** The names of objects of a type, as entities name them. */
	private static JsonArray objectNames(Iterable<? extends MapObject> objs, String prefix,
		Predicate<MapObject> include)
	{
		JsonArray names = new JsonArray();
		for (MapObject obj : objs) {
			if (include.test(obj))
				names.add(prefix + obj.getName());
		}
		return names;
	}

	private void sendLists()
	{
		JsonObject lists = new JsonObject();
		lists.add("items", names(ProjectDatabase.getItemNames()));
		lists.add("gameFlags", names(ProjectDatabase.getSavedFlagNames()));
		lists.add("areaFlags", names(editor.map.areaFlagNames));
		lists.add("mapVars", names(editor.map.mapVarNames));
		lists.add("spawnModes", names(Arrays.asList(ProjectDatabase.EItemSpawnModes.getValues())));
		lists.add("models", objectNames(editor.getGeometryMap().modelTree, "MODEL_", obj -> true));
		lists.add("colliders", objectNames(editor.getCollisionMap().colliderTree, "COLLIDER_", obj -> true));
		lists.add("zones", objectNames(editor.getCollisionMap().zoneTree, "ZONE_", obj -> true));
		lists.add("positions", objectNames(editor.map.markerTree, "",
			obj -> ((Marker) obj).getType() == MarkerType.Position));
		JsonArray spriteNames = new JsonArray();
		for (SpriteMetadata sprite : sprites)
			spriteNames.add(sprite.name);
		lists.add("sprites", spriteNames);

		String text = lists.toString();
		if (!text.equals(listsSent)) {
			listsSent = text;
			JsonObject message = new JsonObject();
			message.addProperty("type", "propertyLists");
			message.add("lists", lists);
			editor.remote().getSession().send(message);
		}
	}

	/**
	 * Sets a property of every selected object, or whether it's used, if `enabled` isn't null. The properties are
	 * sent again after, even if they're the same, so a control that was refused shows its value again.
	 */
	public void set(String key, JsonElement value, Boolean enabled)
	{
		sent = null;
		List<?> objs = selection();
		RemoteField<?> property = properties(objs).stream()
			.filter(p -> p.key.equals(key))
			.findFirst().orElse(null);
		if (property == null)
			return;

		List<AbstractCommand> commands = new ArrayList<>();
		for (Object obj : objs) {
			AbstractCommand command = (enabled != null)
				? property.enabler(obj, enabled)
				: property.setter(obj, value);
			if (command != null)
				commands.add(command);
		}
		if (commands.size() == 1) {
			MapEditor.execute(commands.get(0));
		}
		else if (commands.size() > 1) {
			CommandBatch batch = new CommandBatch("Set " + property.labelOf(objs.get(0)));
			for (AbstractCommand command : commands)
				batch.addCommand(command);
			MapEditor.execute(batch);
		}
	}
}
