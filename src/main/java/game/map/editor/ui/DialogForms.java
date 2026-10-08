package game.map.editor.ui;

import static game.map.shape.TexturePanner.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import common.commands.CommandBatch;
import game.map.Axis;
import game.map.editor.MapEditor;
import game.map.editor.RemoteForm;
import game.map.editor.commands.ApplyPannerToList;
import game.map.editor.selection.Selection;
import game.map.editor.ui.dialogs.TransformSelectionPanel.Transform;
import game.map.editor.ui.dialogs.TransformSelectionPanel.TransformType;
import game.map.shape.Model;
import game.map.shape.TexturePanner;
import game.map.shape.TexturePanner.PannerParams;
import game.map.shape.TexturePanner.SetTexPannerParams;
import game.map.shape.TransformMatrix;
import game.map.shape.UVGenerator;
import game.map.shape.UVGenerator.Projection;
import game.map.shape.UVGenerator.ProjectionAxis;

/** The forms another program shows in place of the map editor's dialogs. See {@link RemoteForm}. */
public class DialogForms
{
	/** The transform last applied, whose values the next starts from. */
	private static final Transform transform = new Transform();

	/**
	 * Transforms the selection: moves, rotates, scales, resizes, or mirrors it, about its center, as `type` does, or
	 * as the last transform did, if it's null.
	 */
	public static RemoteForm transform(MapEditor editor, Selection<?> selection, TransformType type)
	{
		Transform t = transform;
		if (type != null)
			t.type = type;
		t.setSelection(selection);
		TransformType[] types = TransformType.values();
		String[] axes = { "X", "Y", "Z" };
		String[] xyz = { "X", "Y", "Z" };

		RemoteForm form = new RemoteForm("Transform Selection", "Apply");
		form.choice("type", "Transform", names(types), () -> t.type.ordinal(), i -> t.type = types[i]);
		form.vector("translate", "Offset", xyz, true,
			() -> new double[] { t.translate[0], t.translate[1], t.translate[2] },
			(i, value) -> t.translate[i] = (int) Math.round(value))
			.shown(obj -> t.type == TransformType.Translate);
		form.choice("axis", "Axis", axes, () -> t.axis.ordinal(), i -> t.axis = Axis.values()[i])
			.shown(obj -> t.type == TransformType.Rotate);
		form.number("angle", "Angle", () -> t.angle, value -> t.angle = value)
			.shown(obj -> t.type == TransformType.Rotate);
		form.vector("scale", "Scale", xyz, false, () -> t.scale.clone(), (i, value) -> {
			if (uniformScale[0])
				Arrays.fill(t.scale, value);
			else
				t.scale[i] = value;
		}).shown(obj -> t.type == TransformType.Scale);
		form.checkbox("uniform", "Scale uniformly", () -> uniformScale[0], value -> uniformScale[0] = value)
			.shown(obj -> t.type == TransformType.Scale);
		form.vector("resize", "Size", xyz, true,
			() -> new double[] { t.resize[0], t.resize[1], t.resize[2] },
			(i, value) -> t.resize[i] = (int) Math.max(0, Math.round(value)))
			.shown(obj -> t.type == TransformType.Resize);
		for (int i = 0; i < 3; i++) {
			int axis = i;
			form.checkbox("flip" + xyz[i], "Mirror " + xyz[i], () -> t.flip[axis], value -> t.flip[axis] = value)
				.shown(obj -> t.type == TransformType.Flip);
		}

		form.onSubmit(() -> {
			TransformMatrix m = t.createMatrix();
			if (m != null)
				editor.runEditorAction(() -> editor.action_TransformSelection(m, selection));
		});
		return form;
	}

	/** The UV projection last generated, whose options the next starts from. */
	private static Projection projection = Projection.PLANAR;
	private static ProjectionAxis projectionAxis = ProjectionAxis.Y;
	private static double uvScale = -1;

	/** Generates the selected models' UVs by projecting their vertices. */
	public static RemoteForm generateUVs(MapEditor editor)
	{
		if (uvScale < 0)
			uvScale = editor.getDefaultUVScale();
		String[] projections = { "Planar", "Polar", "Cylindrical", "Spherical", "Only Z" };
		String[] axes = { "X", "Y", "Z", "Normal" };

		RemoteForm form = new RemoteForm("Generate UVs", "Generate");
		form.choice("projection", "Projection", projections, () -> projection.ordinal(),
			i -> projection = Projection.values()[i]);
		form.choice("axis", "About axis", axes, () -> projectionAxis.ordinal(),
			i -> projectionAxis = ProjectionAxis.values()[i]);
		form.number("scale", "UV scale", () -> uvScale, value -> uvScale = Math.max(0.0125, value));
		form.onSubmit(() -> {
			UVGenerator generator = new UVGenerator(projection, projectionAxis, (float) uvScale);
			editor.runEditorAction(() -> editor.action_GenerateUVs(generator));
		});
		return form;
	}

	/** Whether scaling the selection scales it the same along each axis. */
	private static final boolean[] uniformScale = { false };

	private static String[] names(Object[] values)
	{
		String[] names = new String[values.length];
		for (int i = 0; i < values.length; i++)
			names[i] = values[i].toString();
		return names;
	}

	/**
	 * The map's texture panners, starting with `first`, whose changes play as they're made, and are undone together
	 * once they're applied.
	 */
	public static RemoteForm panners(MapEditor editor, TexturePanner first)
	{
		List<TexturePanner> panners = new ArrayList<>();
		for (TexturePanner panner : editor.map.features.texPanners)
			panners.add(panner);
		String[] names = new String[panners.size()];
		for (int i = 0; i < names.length; i++)
			names[i] = "Panner " + i;

		// each panner changed, with its parameters before then
		Map<TexturePanner, PannerParams> originals = new LinkedHashMap<>();
		TexturePanner[] current = { first };
		Supplier<PannerParams> params = () -> {
			originals.computeIfAbsent(current[0], panner -> panner.params.get());
			return current[0].params;
		};

		RemoteForm form = new RemoteForm("Texture Panners", "OK");
		form.choice("panner", "Panner", names, () -> current[0].panID, i -> current[0] = panners.get(i));
		form.checkbox("useTexels", "Use texel units", () -> current[0].params.useTexels,
			value -> params.get().useTexels = value);
		form.checkbox("generate", "Always generate", () -> current[0].params.generate,
			value -> params.get().generate = value);

		for (String layer : new String[] { "Main", "Aux" }) {
			// the first of the layer's two coordinates, as tracks and as coordinates in either unit
			int track = layer.equals("Main") ? MAIN_U : AUX_U;
			String key = layer.toLowerCase();
			form.heading(key + "Heading", layer);
			form.vector(key + "Delay", "Delay", new String[] { "U", "V" }, true,
				() -> new double[] { current[0].params.freq[track], current[0].params.freq[track + 1] },
				(i, value) -> params.get().freq[track + i] = (int) Math.round(value));
			for (boolean texels : new boolean[] { false, true }) {
				int coord = track + (texels ? MAIN_S : MAIN_U);
				String[] components = texels ? new String[] { "S", "T" } : new String[] { "U", "V" };
				String units = texels ? "Texels" : "";
				form.vector(key + "Rate" + units, "Rate", components, true,
					() -> new double[] { current[0].params.rate[coord], current[0].params.rate[coord + 1] },
					(i, value) -> setRate(params.get(), coord + i, (int) Math.round(value)))
					.shown(obj -> current[0].params.useTexels == texels);
				form.vector(key + "Offset" + units, "Offset", components, true,
					() -> new double[] { current[0].params.init[coord], current[0].params.init[coord + 1] },
					(i, value) -> params.get().init[coord + i] = (int) Math.round(value))
					.shown(obj -> current[0].params.useTexels == texels);
			}
		}
		form.integer("max", "Max value", () -> current[0].params.getMax(), value -> params.get().setMax(value));

		form.button("apply", "Apply to selected models", () -> {
			List<Model> models = editor.selectionManager.getSelectedObjects(Model.class);
			if (!models.isEmpty())
				MapEditor.execute(new ApplyPannerToList(models, current[0].panID));
		});
		form.button("select", "Select models", () -> {
			List<Model> models = new ArrayList<>();
			for (Model mdl : editor.map.modelTree) {
				if (mdl.pannerID.get() == current[0].panID)
					models.add(mdl);
			}
			if (!models.isEmpty())
				MapEditor.execute(editor.selectionManager.getSetObjects(models));
		});

		form.onSubmit(() -> {
			CommandBatch batch = new CommandBatch("Set Tex Panner Params");
			originals.forEach((panner, original) -> batch.addCommand(new SetTexPannerParams(panner, original)));
			if (!originals.isEmpty())
				MapEditor.execute(batch);
		});
		form.onCancel(() -> originals.forEach((panner, original) -> {
			panner.params.set(original);
			panner.reset();
		}));
		return form;
	}

	/** Sets a coordinate's rate, and the delay of its track to update it every frame, if it had none, as needed. */
	private static void setRate(PannerParams params, int coord, int rate)
	{
		params.rate[coord] = rate;
		int track = coord % NUM_TRACKS;
		if (rate != 0 && params.freq[track] == 0)
			params.freq[track] = 1;
		if (rate == 0 && params.freq[track] == 1)
			params.freq[track] = 0;
	}
}
