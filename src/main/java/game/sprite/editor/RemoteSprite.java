package game.sprite.editor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import assets.AssetManager;
import common.commands.AbstractCommand;
import game.sprite.ImgAsset;
import game.sprite.PalAsset;
import game.sprite.Sprite;
import game.sprite.SpriteAnimation;
import game.sprite.SpriteComponent;
import game.sprite.SpritePalette;
import game.sprite.SpriteRaster;
import game.sprite.SpriteRasterFace;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.GotoKey;
import game.sprite.editor.animators.keyframe.Keyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimatorEditor;
import game.sprite.editor.animators.keyframe.LoopKey;
import game.sprite.editor.animators.keyframe.NotifyKey;
import game.sprite.editor.animators.keyframe.ParentKey;
import game.sprite.editor.commands.BindImgAsset;
import game.sprite.editor.commands.BindPalAsset;
import game.sprite.editor.commands.RenameAnimation;
import game.sprite.editor.commands.RenameComponent;
import game.sprite.editor.commands.RenamePalette;
import game.sprite.editor.commands.RenameRaster;
import game.sprite.editor.commands.ReorderAnimation;
import game.sprite.editor.commands.ReorderCommand;
import game.sprite.editor.commands.ReorderComponent;
import game.sprite.editor.commands.ReorderPalette;
import game.sprite.editor.commands.ReorderRaster;
import game.sprite.editor.commands.ToggleComponentHidden;
import game.sprite.editor.commands.keyframe.SetKeyframeDuration;
import game.sprite.editor.commands.keyframe.SetKeyframeEnablePalette;
import game.sprite.editor.commands.keyframe.SetKeyframeEnableRaster;
import game.sprite.editor.commands.keyframe.SetKeyframeName;
import game.sprite.editor.commands.keyframe.SetKeyframePalette;
import game.sprite.editor.commands.keyframe.SetKeyframePosition;
import game.sprite.editor.commands.keyframe.SetKeyframeRaster;
import game.sprite.editor.commands.keyframe.SetKeyframeRotation;
import game.sprite.editor.commands.keyframe.SetKeyframeScale;

/**
 * The sprite editor's state, which another program shows in place of its side panels, and the edits it makes, which
 * are made by the same commands as the side panels' are. It's sent, whenever they change:
 * <ul>
 * <li>"sprite": the names of the sprite's animations, each with its components, of its rasters and palettes, and of
 * the images in its rasters and palettes directories, which they show;</li>
 * <li>"previews": an image of each raster, and of each image the rasters can show, as data URLs, and the colors of each
 * palette, and of each image palettes can take colors from, a second or so after they change;</li>
 * <li>"state": the editor's mode, and the indices of the animation, component, keyframe, raster, and palette
 * selected, or -1;</li>
 * <li>"keyframes": a track of keyframes for each component of the selected animation, each keyframe with its kind,
 * label, duration, and when it starts, and for a goto or a repeat, the index of the keyframe it goes to, and a
 * repeat's count;</li>
 * <li>"playback": whether the animation is playing, and its time;</li>
 * <li>"properties": the fields of the selected component and keyframe, raster, or palette, as the mode shows, in the
 * map editor's form;</li>
 * <li>"open": a file to open, which a field's button asks for.</li>
 * <li>"dirty": whether the sprite has changes to save.</li>
 * </ul>
 */
class RemoteSprite
{
	private static final String[] MODES = { "rasters", "palettes", "animations" };

	private final SpriteEditor editor;
	private int frame = 0;
	private String spriteSent;
	private String stateSent;
	private String keyframesSent;
	private String playbackSent;
	private String propertiesSent;
	private String previewsSent;
	/** The image last selected in the view, whose raster was selected for it. */
	private ImgAsset imageSelected;
	private Boolean dirtySent;

	RemoteSprite(SpriteEditor editor)
	{
		this.editor = editor;

		// another program starts on the animations, without an undoable command
		SwingUtilities.invokeLater(() -> {
			editor.suppressCommands = true;
			editor.editorModeTabs.setSelectedIndex(2);
			editor.suppressCommands = false;
			editor.setModesTab(2);
		});
	}

	private void send(JsonObject message)
	{
		editor.remote().getSession().send(message);
	}

	/** Sends `message`, of `type`, unless it's what was sent last, and returns it as text, to compare the next with. */
	private String sendChanged(String type, JsonObject message, String sent)
	{
		message.addProperty("type", type);
		String text = message.toString();
		if (!text.equals(sent))
			send(message);
		return text;
	}

	void send()
	{
		Sprite sprite = editor.getSprite();
		if (sprite == null)
			return;

		// the whole sprite is a few times a second
		if (spriteSent == null || frame++ % 10 == 0)
			spriteSent = sendChanged("sprite", describeSprite(sprite), spriteSent);

		// encoding each raster's image is slower, so once a second
		if (frame % 30 == 0)
			previewsSent = sendChanged("previews", describePreviews(sprite), previewsSent);

		selectRasterOfImage(sprite);
		stateSent = sendChanged("state", describeState(), stateSent);
		keyframesSent = sendChanged("keyframes", describeKeyframes(), keyframesSent);
		playbackSent = sendChanged("playback", describePlayback(), playbackSent);
		propertiesSent = sendChanged("properties", describeProperties(), propertiesSent);

		boolean dirty = editor.isModified();
		if (!Boolean.valueOf(dirty).equals(dirtySent)) {
			dirtySent = dirty;
			JsonObject message = new JsonObject();
			message.addProperty("type", "dirty");
			message.addProperty("dirty", dirty);
			send(message);
		}
	}

	private static JsonArray names(Iterable<?> objs)
	{
		JsonArray names = new JsonArray();
		for (Object obj : objs)
			names.add(obj.toString());
		return names;
	}

	private JsonObject describeSprite(Sprite sprite)
	{
		JsonObject message = new JsonObject();
		message.addProperty("name", sprite.name);
		JsonArray animations = new JsonArray();
		for (SpriteAnimation anim : sprite.animations) {
			JsonObject animation = new JsonObject();
			animation.addProperty("name", anim.name);
			if (anim.hasError())
				animation.addProperty("error", anim.getErrorMsg());
			JsonArray components = new JsonArray();
			for (SpriteComponent comp : anim.components) {
				JsonObject component = new JsonObject();
				component.addProperty("name", comp.name);
				component.addProperty("hidden", comp.hidden);
				if (comp.hasError())
					component.addProperty("error", comp.getErrorMsg());
				components.add(component);
			}
			animation.add("components", components);
			animations.add(animation);
		}
		message.add("animations", animations);
		JsonArray rasters = new JsonArray();
		for (SpriteRaster raster : sprite.rasters)
			rasters.add(describeItem(raster.name, raster));
		message.add("rasters", rasters);
		JsonArray palettes = new JsonArray();
		for (SpritePalette palette : sprite.palettes)
			palettes.add(describeItem(palette.name, palette));
		message.add("palettes", palettes);
		message.add("images", names(sprite.imgAssets));
		message.add("paletteImages", names(sprite.palAssets));
		message.addProperty("hasBack", sprite.hasBack);
		return message;
	}

	private static JsonObject describeItem(String name, Editable editable)
	{
		JsonObject item = new JsonObject();
		item.addProperty("name", name);
		if (editable.hasError())
			item.addProperty("error", editable.getErrorMsg());
		return item;
	}

	/** An image as a data URL, or an empty one if it isn't loaded yet. */
	private static String dataUrl(BufferedImage image)
	{
		if (image == null)
			return "";
		try {
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(image, "png", png);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		}
		catch (IOException e) {
			return "";
		}
	}

	private JsonObject describePreviews(Sprite sprite)
	{
		JsonArray rasters = new JsonArray();
		for (SpriteRaster raster : sprite.rasters)
			rasters.add(raster.front.asset == null ? "" : dataUrl(raster.front.preview.getImage()));
		JsonObject images = new JsonObject();
		for (ImgAsset image : sprite.imgAssets)
			images.addProperty(image.toString(), dataUrl(image.preview.getImage()));
		JsonObject palettes = new JsonObject();
		for (SpritePalette palette : sprite.palettes) {
			if (palette.asset != null)
				palettes.add(palette.toString(), colors(palette.asset));
		}
		JsonObject paletteImages = new JsonObject();
		for (PalAsset asset : sprite.palAssets)
			paletteImages.add(asset.toString(), colors(asset));

		JsonObject message = new JsonObject();
		message.add("rasters", rasters);
		message.add("images", images);
		message.add("palettes", palettes);
		message.add("paletteImages", paletteImages);
		return message;
	}

	/** A palette's colors, as RGB numbers. */
	private static JsonArray colors(PalAsset asset)
	{
		JsonArray colors = new JsonArray();
		for (int i = 0; i < asset.savedColors.length; i++)
			colors.add(asset.pal.getColor(i).getRGB() & 0xFFFFFF);
		return colors;
	}

	/**
	 * Selects the raster of the image that a click in the view selects, as the other program shows rasters, and not
	 * the images they show.
	 */
	private void selectRasterOfImage(Sprite sprite)
	{
		ImgAsset image = sprite.selectedImgAsset;
		if (image == imageSelected)
			return;
		imageSelected = image;
		RastersList list = editor.rastersTab.rasterList();
		SpriteRaster selected = list.getSelectedValue();
		if (image == null || (selected != null && selected.front.asset == image))
			return;
		for (SpriteRaster raster : sprite.rasters) {
			if (raster.front.asset == image) {
				SwingUtilities.invokeLater(() -> list.setSelectedValue(raster, true));
				return;
			}
		}
	}

	private JsonObject describeState()
	{
		JsonObject message = new JsonObject();
		message.addProperty("mode", MODES[editor.getModesTab()]);
		SpriteAnimation anim = editor.getAnimation();
		SpriteComponent comp = editor.getComponent();
		message.addProperty("animation", anim == null ? -1 : anim.getIndex());
		message.addProperty("component", comp == null ? -1 : comp.listIndex);
		message.addProperty("keyframe", comp == null ? -1 : KeyframeAnimatorEditor.list().getSelectedIndex());
		message.addProperty("raster", editor.rastersTab.rasterList().getSelectedIndex());
		message.addProperty("palette", editor.palettesTab.paletteList().getSelectedIndex());
		return message;
	}

	/** The keyframes of the selected component, which are none if none is selected. */
	private List<AnimKeyframe> keyframes()
	{
		// the list has no keyframes of its own until a component is first shown
		if (editor.getComponent() == null
			|| !(KeyframeAnimatorEditor.list().getModel() instanceof DefaultListModel<AnimKeyframe> model))
			return List.of();
		return elements(model);
	}

	private static <T> List<T> elements(DefaultListModel<T> model)
	{
		return Collections.list(model.elements());
	}

	private static <T> List<T> assets(SpriteAssetCollection<T> objs)
	{
		List<T> list = new ArrayList<>();
		objs.forEach(list::add);
		return list;
	}

	private static String kindOf(AnimElement elem)
	{
		if (elem instanceof Keyframe)
			return "keyframe";
		if (elem instanceof LoopKey)
			return "loop";
		if (elem instanceof GotoKey)
			return "goto";
		if (elem instanceof ParentKey)
			return "parent";
		return "notify";
	}

	/** The components of the selected animation, which are none if none is selected. */
	private List<SpriteComponent> components()
	{
		SpriteAnimation anim = editor.getAnimation();
		return (anim == null) ? List.of() : elements(anim.components);
	}

	private JsonObject describeKeyframes()
	{
		JsonArray tracks = new JsonArray();
		for (SpriteComponent comp : components()) {
			JsonArray list = new JsonArray();
			for (AnimKeyframe elem : comp.getKeyframeAnimator().keyframes) {
				JsonObject key = new JsonObject();
				key.addProperty("kind", kindOf(elem));
				key.addProperty("label", elem.toString());
				key.addProperty("duration", (elem instanceof Keyframe kf) ? kf.duration : 0);
				key.addProperty("time", elem.animTime);
				if (elem instanceof Keyframe kf && kf.setImage && kf.img != null)
					key.addProperty("raster", kf.img.getIndex());
				if (elem instanceof GotoKey go2)
					key.addProperty("target", comp.getKeyframeAnimator().keyframes.indexOf(go2.target));
				if (elem instanceof LoopKey loop) {
					key.addProperty("target", comp.getKeyframeAnimator().keyframes.indexOf(loop.target));
					key.addProperty("count", loop.count);
				}
				if (elem.hasError())
					key.addProperty("error", elem.getErrorMsg());
				list.add(key);
			}
			JsonObject track = new JsonObject();
			track.addProperty("name", comp.name);
			track.addProperty("hidden", comp.hidden);
			track.add("keyframes", list);
			tracks.add(track);
		}
		JsonObject message = new JsonObject();
		message.add("tracks", tracks);
		return message;
	}

	private JsonObject describePlayback()
	{
		JsonObject message = new JsonObject();
		SpriteAnimation anim = editor.getAnimation();
		message.addProperty("playing", !editor.paused);
		message.addProperty("time", anim == null ? 0 : Math.max(anim.animTime - 2, 0));
		return message;
	}

	/** Sets how the animation is drawn, which the other program keeps. */
	private void setOption(String name, boolean value)
	{
		switch (name) {
			case "background" -> editor.showBackground = value;
			case "axes" -> editor.showAxes = value;
			case "guide" -> editor.showGuide = value;
			case "flip" -> editor.flipHorizontal = value;
			case "back" -> editor.useBackFacing = value;
			case "filtering" -> editor.useFiltering = value;
			case "highlight" -> editor.highlightComponent = value;
			case "currentOnly" -> SwingUtilities.invokeLater(() -> editor.cbShowOnlySelectedComponent.setSelected(value));
		}
	}

	private static JsonObject field(String key, String kind, String label)
	{
		JsonObject field = new JsonObject();
		field.addProperty("key", key);
		field.addProperty("kind", kind);
		field.addProperty("label", label);
		return field;
	}

	private static JsonObject vector(String key, String label, int x, int y, int z)
	{
		JsonObject field = field(key, "vector", label);
		JsonArray components = new JsonArray();
		components.add("X");
		components.add("Y");
		components.add("Z");
		field.add("components", components);
		field.addProperty("integer", true);
		JsonArray value = new JsonArray();
		value.add(x);
		value.add(y);
		value.add(z);
		field.add("value", value);
		return field;
	}

	/** A choice of `objs`, after a first option of none, `none`, whose value is `selected`'s index, or 0 for none. */
	private static JsonObject choice(String key, String label, String none, Iterable<?> objs, Object selected)
	{
		JsonObject field = field(key, "choice", label);
		JsonArray options = new JsonArray();
		options.add(none);
		int value = 0;
		int i = 1;
		for (Object obj : objs) {
			options.add(obj.toString());
			if (obj == selected)
				value = i;
			i++;
		}
		field.add("options", options);
		field.addProperty("value", value);
		return field;
	}

	/**
	 * Has a choice show images beside its options, from the set `icons` names: "rasters", "images", "palettes", or
	 * "paletteImages", as "previews" sends them.
	 */
	private static JsonObject withIcons(JsonObject choice, String icons)
	{
		choice.addProperty("icons", icons);
		return choice;
	}

	private JsonObject describeProperties()
	{
		return switch (editor.getModesTab()) {
			case 0 -> describeRaster();
			case 1 -> describePalette();
			default -> describeAnimation();
		};
	}

	private JsonObject describeRaster()
	{
		JsonObject message = new JsonObject();
		SpriteRaster raster = editor.rastersTab.rasterList().getSelectedValue();
		if (raster == null)
			return message;

		Sprite sprite = raster.parentSprite;
		message.addProperty("title", raster.name);
		message.addProperty("subtitle", "Raster");
		JsonArray fields = new JsonArray();
		fields.add(withIcons(choice("r.image", "Image", "None", sprite.imgAssets, raster.front.asset), "images"));
		fields.add(withIcons(choice("r.palette", "Palette", "None", sprite.palettes, raster.front.pal), "palettes"));
		if (sprite.hasBack) {
			fields.add(field("back", "heading", "Back"));
			JsonObject separate = field("r.separateBack", "checkbox", "Own image");
			separate.addProperty("value", raster.hasIndependentBack);
			fields.add(separate);
			if (raster.hasIndependentBack)
				fields.add(withIcons(choice("r.backImage", "Image", "None", sprite.imgAssets, raster.back.asset), "images"));
			fields.add(withIcons(choice("r.backPalette", "Palette", "None", sprite.palettes, raster.back.pal), "palettes"));
		}
		if (raster.front.asset != null)
			fields.add(field("r.open", "button", "Open Image"));
		message.add("properties", fields);
		return message;
	}

	private JsonObject describePalette()
	{
		JsonObject message = new JsonObject();
		SpritePalette palette = editor.palettesTab.paletteList().getSelectedValue();
		if (palette == null)
			return message;

		message.addProperty("title", palette.name);
		message.addProperty("subtitle", "Palette");
		JsonArray fields = new JsonArray();
		fields.add(withIcons(choice("p.image", "Image", "None", palette.parentSprite.palAssets, palette.asset),
			"paletteImages"));
		JsonObject frontOnly = field("p.frontOnly", "checkbox", "Front only");
		frontOnly.addProperty("value", palette.frontOnly);
		fields.add(frontOnly);
		if (palette.asset != null) {
			JsonObject colors = field("p.colors", "swatches", "Colors");
			JsonArray values = new JsonArray();
			for (int i = 0; i < palette.asset.savedColors.length; i++)
				values.add(palette.asset.pal.getColor(i).getRGB() & 0xFFFFFF);
			colors.add("value", values);
			fields.add(colors);
			fields.add(field("p.open", "button", "Open Image"));
		}
		message.add("properties", fields);
		return message;
	}

	private JsonObject describeAnimation()
	{
		JsonObject message = new JsonObject();
		SpriteComponent comp = editor.getComponent();
		if (comp == null)
			return message;

		message.addProperty("title", comp.name);
		message.addProperty("subtitle", comp.parentAnimation.name);
		JsonArray fields = new JsonArray();
		fields.add(vector("comp.offset", "Offset", comp.posx, comp.posy, comp.posz));

		AnimElement elem = KeyframeAnimatorEditor.list().getSelectedValue();
		if (elem instanceof Keyframe kf) {
			// named here, as its name is renamed from the timeline
			fields.add(field("keyframe", "heading", kf.name.isEmpty() ? "Keyframe" : kf.name));
			JsonObject duration = field("kf.duration", "number", "Duration");
			duration.addProperty("integer", true);
			duration.addProperty("value", kf.duration);
			fields.add(duration);
			Sprite sprite = comp.sprite;
			JsonObject raster = withIcons(choice("kf.raster", "Raster", "None", sprite.rasters, kf.img), "rasters");
			raster.addProperty("optional", true);
			raster.addProperty("enabled", kf.setImage);
			fields.add(raster);
			JsonObject palette = withIcons(choice("kf.palette", "Palette", "Raster's palette", sprite.palettes, kf.pal),
				"palettes");
			palette.addProperty("optional", true);
			palette.addProperty("enabled", kf.setPalette);
			fields.add(palette);
			fields.add(vector("kf.position", "Position", kf.dx, kf.dy, kf.dz));
			fields.add(vector("kf.rotation", "Rotation", kf.rx, kf.ry, kf.rz));
			fields.add(vector("kf.scale", "Scale", kf.sx, kf.sy, kf.sz));
		}
		else if (elem instanceof LoopKey loop) {
			fields.add(field("keyframe", "heading", "Repeat"));
			fields.add(choice("loop.target", "From", "None", keyframesOnly(), loop.target));
			// how many times it plays the keyframes, as the timeline counts them, which is once more than it repeats
			JsonObject count = field("loop.count", "number", "Plays");
			count.addProperty("integer", true);
			count.addProperty("value", loop.count + 1);
			fields.add(count);
		}
		else if (elem instanceof GotoKey go) {
			// as the timeline's menu names one that goes back
			boolean back = go.target != null && keyframes().indexOf(go.target) <= keyframes().indexOf(go);
			fields.add(field("keyframe", "heading", back ? "Loop" : "Go to"));
			fields.add(choice("goto.target", "Keyframe", "None", keyframesOnly(), go.target));
		}
		else if (elem instanceof ParentKey parent) {
			fields.add(field("keyframe", "heading", "Parent"));
			fields.add(choice("parent.parent", "Component", "None", comp.parentAnimation.components, parent.parent));
		}
		else if (elem instanceof NotifyKey notify) {
			fields.add(field("keyframe", "heading", "Notify"));
			JsonObject value = field("notify.value", "number", "Value");
			value.addProperty("integer", true);
			value.addProperty("value", notify.value);
			fields.add(value);
		}
		message.add("properties", fields);
		return message;
	}

	/** The keyframes of the selected component, without its repeats, gotos, and the like. */
	private List<Keyframe> keyframesOnly()
	{
		return keyframes().stream()
			.filter(elem -> elem instanceof Keyframe)
			.map(elem -> (Keyframe) elem)
			.toList();
	}

	/** The element of `objs` an index from a choice made by {@link #choice} is, or null for none. */
	private static <T> T chosen(List<T> objs, JsonElement value)
	{
		int i = value.getAsInt() - 1;
		return (i >= 0 && i < objs.size()) ? objs.get(i) : null;
	}

	/**
	 * Sets a field of `target` that has no command of its own, as an undoable command, which runs `after` once it's
	 * set either way.
	 */
	private static <T> AbstractCommand setValue(String name, Editable target, T prev, T next, Consumer<T> set,
		Runnable after)
	{
		return new AbstractCommand(name) {
			@Override
			public void exec()
			{
				super.exec();
				set.accept(next);
				after.run();
				target.incrementModified();
			}

			@Override
			public void undo()
			{
				super.undo();
				set.accept(prev);
				after.run();
				target.decrementModified();
			}
		};
	}

	/** Sets a field of a keyframe that has no command of its own, as an undoable command. */
	private static <T> AbstractCommand setValue(String name, AnimElement elem, T prev, T next, Consumer<T> set)
	{
		return setValue(name, elem, prev, next, set, elem.owner::calculateTiming);
	}

	private void open(File file)
	{
		JsonObject message = new JsonObject();
		message.addProperty("type", "open");
		message.addProperty("file", file.getAbsolutePath());
		send(message);
	}

	/** Sets a property of the selected raster. */
	private AbstractCommand setRasterProperty(String key, JsonElement value)
	{
		SpriteRaster raster = editor.rastersTab.rasterList().getSelectedValue();
		if (raster == null)
			return null;
		Sprite sprite = raster.parentSprite;
		return switch (key) {
			case "r.name" -> {
				String name = value.getAsString().trim();
				String unique = (name.isBlank() || name.equals(raster.name)) ? null : raster.createUniqueName(name);
				yield (unique == null) ? null : new RenameRaster(editor.rastersTab.rasterList(), raster, unique);
			}
			case "r.image" -> new BindImgAsset(editor.rastersTab, raster.front, chosen(assets(sprite.imgAssets), value), face -> {});
			case "r.backImage" -> new BindImgAsset(editor.rastersTab, raster.back, chosen(assets(sprite.imgAssets), value), face -> {});
			case "r.palette", "r.backPalette" -> {
				SpriteRasterFace face = key.equals("r.palette") ? raster.front : raster.back;
				yield setValue("Assign Palette", raster, face.pal, chosen(elements(sprite.palettes), value),
					face::assignPal, raster::loadEditorImages);
			}
			case "r.separateBack" -> setValue(value.getAsBoolean() ? "Use Separate Assets" : "Use Shared Asset", raster,
				raster.hasIndependentBack, value.getAsBoolean(), v -> raster.hasIndependentBack = v, () -> {});
			case "r.open" -> {
				if (raster.front.asset != null)
					open(raster.front.asset.getSource());
				yield null;
			}
			default -> null;
		};
	}

	/** Sets a property of the selected palette. */
	private AbstractCommand setPaletteProperty(String key, JsonElement value)
	{
		SpritePalette palette = editor.palettesTab.paletteList().getSelectedValue();
		if (palette == null)
			return null;
		Sprite sprite = palette.parentSprite;
		return switch (key) {
			case "p.name" -> {
				String name = value.getAsString().trim();
				String unique = (name.isBlank() || name.equals(palette.name)) ? null : palette.createUniqueName(name);
				yield (unique == null) ? null : new RenamePalette(editor.palettesTab.paletteList(), palette, unique);
			}
			case "p.image" -> new BindPalAsset(editor.palettesTab, palette, chosen(assets(sprite.palAssets), value));
			case "p.frontOnly" -> setValue("Set Front Only", palette, palette.frontOnly, value.getAsBoolean(),
				v -> palette.frontOnly = v, () -> {});
			case "p.colors" -> {
				PalAsset asset = palette.asset;
				int index = value.getAsJsonArray().get(0).getAsInt();
				if (asset == null || index < 0 || index >= asset.savedColors.length)
					yield null;
				// keeps the color's alpha, such as the first's, which is clear
				Color prev = asset.pal.getColor(index);
				int rgb = value.getAsJsonArray().get(1).getAsInt();
				Color next = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, prev.getAlpha());
				yield setValue("Set Color", asset, prev, next, color -> {
					asset.pal.setColor(index, color);
					asset.savedColors[index] = color;
					asset.dirty = true;
				}, () -> sprite.loadEditorImages(asset));
			}
			case "p.open" -> {
				if (palette.asset != null)
					open(palette.asset.getSource());
				yield null;
			}
			default -> null;
		};
	}

	/** Sets a property of the selected component or keyframe, or whether it's used, if `enabled` isn't null. */
	private void setProperty(String key, JsonElement value, Boolean enabled)
	{
		// sent again, even if it's the same, so a control that was refused shows its value again
		propertiesSent = null;

		AbstractCommand rasterOrPalette = key.startsWith("r.") ? setRasterProperty(key, value)
			: key.startsWith("p.") ? setPaletteProperty(key, value)
			: null;
		if (rasterOrPalette != null) {
			SpriteEditor.execute(rasterOrPalette);
			return;
		}

		SpriteComponent comp = editor.getComponent();
		if (comp == null)
			return;
		AnimElement elem = KeyframeAnimatorEditor.list().getSelectedValue();
		Runnable none = () -> {};
		// a vector's value is the index of its number, and the number
		int axis = (value != null && value.isJsonArray()) ? value.getAsJsonArray().get(0).getAsInt() : 0;
		int number = (value != null && value.isJsonArray()) ? value.getAsJsonArray().get(1).getAsInt() : 0;

		AbstractCommand command = null;
		if (key.equals("comp.offset")) {
			command = editor.new SetComponentPosCommand(comp, axis, number);
		}
		else if (elem instanceof Keyframe kf) {
			command = switch (key) {
				case "kf.duration" -> new SetKeyframeDuration(kf, value.getAsInt(), none);
				case "kf.raster" -> (enabled != null)
					? new SetKeyframeEnableRaster(kf, enabled, none)
					: new SetKeyframeRaster(kf, chosen(elements(comp.sprite.rasters), value), none);
				case "kf.palette" -> (enabled != null)
					? new SetKeyframeEnablePalette(kf, enabled, none)
					: new SetKeyframePalette(kf, chosen(elements(comp.sprite.palettes), value), none);
				case "kf.position" -> new SetKeyframePosition(kf, axis, number, none);
				case "kf.rotation" -> new SetKeyframeRotation(kf, axis, number, none);
				case "kf.scale" -> new SetKeyframeScale(kf, axis, number, none);
				default -> null;
			};
		}
		else if (elem instanceof LoopKey loop) {
			if (key.equals("loop.target"))
				command = setValue("Set Repeat Target", loop, loop.target, chosen(keyframesOnly(), value), v -> loop.target = v);
			else if (key.equals("loop.count"))
				// at least twice, as a repeat of none would go back forever, as the game runs it
				command = setValue("Set Repeat Count", loop, loop.count, Math.max(value.getAsInt() - 1, 1),
					v -> loop.count = v);
		}
		else if (elem instanceof GotoKey go) {
			if (key.equals("goto.target"))
				command = setValue("Set Goto Target", go, go.target, chosen(keyframesOnly(), value), v -> go.target = v);
		}
		else if (elem instanceof ParentKey parent) {
			if (key.equals("parent.parent"))
				command = setValue("Set Parent", parent, parent.parent,
					chosen(elements(comp.parentAnimation.components), value), v -> parent.parent = v);
		}
		else if (elem instanceof NotifyKey notify) {
			if (key.equals("notify.value"))
				command = setValue("Set Notify", notify, notify.value, value.getAsInt(), v -> notify.value = v);
		}

		if (command != null)
			SpriteEditor.execute(command);
	}

	/** Selects the element of `list` at `index`, and sets a property of it. */
	private void setPropertyOf(JList<?> list, int index, String key, JsonElement value)
	{
		list.setSelectedIndex(index);
		setProperty(key, value, null);
	}

	/** Runs an action of one of the editor's lists, such as "duplicate", on its element at `index`. */
	private static void listAction(JList<?> list, int index, String action)
	{
		list.setSelectedIndex(index);
		Action listAction = list.getActionMap().get(action);
		if (listAction != null)
			listAction.actionPerformed(null);
	}

	/**
	 * Acts on an animation, component, keyframe, raster, or palette, as `target` says, at `index`: "add",
	 * "duplicate", "copy", "paste", "delete", "rename" to `name`, "move" to `to`, "toggleHidden" for a component,
	 * "reload" the images of rasters or palettes, or for a keyframe, "retarget" a goto or repeat to the keyframe at
	 * `to`, or "addLoop" of `kind` "goto" or "loop", `count` times, after it, back to the keyframe at `from`.
	 */
	private void act(String action, String target, int index, JsonObject message)
	{
		Sprite sprite = editor.getSprite();
		SpriteAnimation anim = editor.getAnimation();
		SpriteComponent comp = editor.getComponent();
		if (sprite == null)
			return;

		switch (target) {
			case "animation" -> {
				switch (action) {
					case "add" -> editor.addAnimation();
					case "rename" -> {
						SpriteAnimation renamed = sprite.animations.get(index);
						String name = message.get("name").getAsString().trim();
						if (name.isBlank() || name.equals(renamed.name))
							return;
						String unique = renamed.createUniqueName(name);
						if (unique != null)
							SpriteEditor.execute(new RenameAnimation(editor.animList, renamed, unique));
					}
					case "move" -> SpriteEditor.execute(
						new ReorderAnimation(editor.animList, sprite.animations.get(index), message.get("to").getAsInt()));
					default -> listAction(editor.animList, index, action);
				}
			}
			case "component" -> {
				if (anim == null)
					return;
				switch (action) {
					case "add" -> editor.addComponent();
					case "rename" -> {
						SpriteComponent renamed = anim.components.get(index);
						String name = message.get("name").getAsString().trim();
						if (name.isBlank() || name.equals(renamed.name))
							return;
						String unique = renamed.createUniqueName(name);
						if (unique != null)
							SpriteEditor.execute(new RenameComponent(editor.compList, renamed, unique));
					}
					case "move" -> SpriteEditor.execute(
						new ReorderComponent(editor.compList, anim.components.get(index), message.get("to").getAsInt()));
					case "toggleHidden" -> SpriteEditor.execute(
						new ToggleComponentHidden(editor.compList, anim.components.get(index)));
					default -> listAction(editor.compList, index, action);
				}
			}
			case "raster" -> {
				RastersList list = editor.rastersTab.rasterList();
				switch (action) {
					case "add" -> editor.rastersTab.addRaster();
					case "reload" -> editor.rastersTab.reloadImages();
					case "rename" -> setPropertyOf(list, index, "r.name", message.get("name"));
					case "move" -> SpriteEditor.execute(
						new ReorderRaster(list, sprite.rasters.get(index), message.get("to").getAsInt()));
					default -> listAction(list, index, action);
				}
			}
			case "palette" -> {
				PalettesList list = editor.palettesTab.paletteList();
				switch (action) {
					case "add" -> editor.palettesTab.addPalette();
					case "reload" -> editor.palettesTab.reloadPalettes();
					case "rename" -> setPropertyOf(list, index, "p.name", message.get("name"));
					case "move" -> SpriteEditor.execute(
						new ReorderPalette(list, sprite.palettes.get(index), message.get("to").getAsInt()));
					default -> listAction(list, index, action);
				}
			}
			case "keyframe" -> {
				if (comp == null)
					return;
				AnimElementsList<AnimKeyframe> list = KeyframeAnimatorEditor.list();
				switch (action) {
					case "add" -> {
						// after the others is before a goto at the end, which would otherwise never reach it
						int size = list.getDefaultModel().size();
						int after = (index == -1 && size >= 2 && list.getDefaultModel().get(size - 1) instanceof GotoKey)
							? size - 2
							: index;
						if (after >= -1 && after < size)
							list.setSelectedIndex(after);
						KeyframeAnimatorEditor.create(message.get("kind").getAsString());
					}
					case "rename" -> {
						if (list.getDefaultModel().get(index) instanceof Keyframe kf)
							SpriteEditor.execute(new SetKeyframeName(kf, message.get("name").getAsString(), () -> {}));
					}
					case "move" -> SpriteEditor.execute(
						new ReorderCommand(list, list.getDefaultModel().get(index), message.get("to").getAsInt()));
					case "retarget" -> {
						// a goto or a repeat goes to another keyframe
						AnimKeyframe elem = list.getDefaultModel().get(index);
						if (!(list.getDefaultModel().get(message.get("to").getAsInt()) instanceof Keyframe to))
							return;
						if (elem instanceof GotoKey go2)
							SpriteEditor.execute(setValue("Set Goto Target", go2, go2.target, to, v -> go2.target = v));
						else if (elem instanceof LoopKey loop)
							SpriteEditor.execute(setValue("Set Repeat Target", loop, loop.target, to, v -> loop.target = v));
					}
					case "addLoop" -> {
						// after the keyframe at `index`, going back to the one at `from`
						if (!(list.getDefaultModel().get(message.get("from").getAsInt()) instanceof Keyframe to))
							return;
						list.setSelectedIndex(index);
						KeyframeAnimatorEditor.createLoop(message.get("kind").getAsString(), to,
							message.has("count") ? message.get("count").getAsInt() : 1);
					}
					default -> listAction(list, index, action);
				}
			}
		}
	}

	void receive(JsonObject message)
	{
		switch (message.get("type").getAsString()) {
			case "save": {
				SwingUtilities.invokeLater(() -> {
					editor.saveCurrent();
					Sprite sprite = editor.getSprite();
					JsonObject reply = new JsonObject();
					reply.addProperty("type", "saved");
					if (sprite != null)
						reply.addProperty("file", AssetManager.getTopLevel(sprite.getAsset()).getAbsolutePath());
					send(reply);
				});
				break;
			}
			case "undo":
				SwingUtilities.invokeLater(editor::undoEDT);
				break;
			case "redo":
				SwingUtilities.invokeLater(editor::redoEDT);
				break;
			case "mode": {
				int index = List.of(MODES).indexOf(message.get("mode").getAsString());
				if (index >= 0)
					SwingUtilities.invokeLater(() -> editor.editorModeTabs.setSelectedIndex(index));
				break;
			}
			case "select": {
				int animation = message.has("animation") ? message.get("animation").getAsInt() : -2;
				int component = message.has("component") ? message.get("component").getAsInt() : -2;
				int keyframe = message.has("keyframe") ? message.get("keyframe").getAsInt() : -2;
				int raster = message.has("raster") ? message.get("raster").getAsInt() : -2;
				int palette = message.has("palette") ? message.get("palette").getAsInt() : -2;
				SwingUtilities.invokeLater(() -> {
					if (animation > -2)
						editor.animList.setSelectedIndex(animation);
					if (component > -2)
						editor.compList.setSelectedIndex(component);
					if (keyframe > -2 && editor.getComponent() != null)
						KeyframeAnimatorEditor.list().setSelectedIndex(keyframe);
					if (raster > -2) {
						RastersList list = editor.rastersTab.rasterList();
						list.setSelectedIndex(raster);
						// which the view outlines
						if (list.getSelectedValue() != null && list.getSelectedValue().front.asset != null)
							editor.rastersTab.selectAsset(list.getSelectedValue().front.asset);
					}
					if (palette > -2)
						editor.palettesTab.paletteList().setSelectedIndex(palette);
				});
				break;
			}
			case "playback":
				editor.remotePlayback(message.get("action").getAsString(),
					message.has("time") ? message.get("time").getAsInt() : 0);
				break;
			case "setProperty": {
				String key = message.get("key").getAsString();
				JsonElement value = message.get("value");
				Boolean enabled = message.has("enabled") ? message.get("enabled").getAsBoolean() : null;
				SwingUtilities.invokeLater(() -> setProperty(key, value, enabled));
				break;
			}
			case "action": {
				String action = message.get("action").getAsString();
				String target = message.get("target").getAsString();
				int index = message.has("index") ? message.get("index").getAsInt() : -1;
				SwingUtilities.invokeLater(() -> act(action, target, index, message));
				break;
			}
			case "options":
				for (var option : message.entrySet()) {
					if (option.getValue().isJsonPrimitive() && option.getValue().getAsJsonPrimitive().isBoolean())
						setOption(option.getKey(), option.getValue().getAsBoolean());
				}
				break;
		}
	}
}
