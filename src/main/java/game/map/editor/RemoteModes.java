package game.map.editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.IdentityHashMap;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import assets.AssetManager;
import assets.AssetSubdir;
import game.map.editor.MapEditor.EditorMode;
import game.map.editor.render.TextureManager;
import game.map.editor.ui.GuiCommand;
import game.map.shape.Model;
import game.texture.ModelTexture;

/**
 * The editor's modes, as another program shows and chooses them, with what the modes for applying textures and
 * painting vertices use: the map's textures, the one selected, and the brush. It's sent them whenever they change:
 * the mode as a "mode" message, the textures as a "textures" message, with a preview of each as a PNG data URL, and
 * the brush as a "brush" message.
 */
public class RemoteModes
{
	private final MapEditor editor;
	private final IdentityHashMap<ModelTexture, String> previews = new IdentityHashMap<>();
	private String modeSent = null;
	private String texturesSent = null;
	private String brushSent = null;
	private int frame = 0;

	public RemoteModes(MapEditor editor)
	{
		this.editor = editor;
	}

	/** Switches to Modify, Texture, or VertexPaint mode. */
	public void setMode(EditorMode mode)
	{
		if (editor.getEditorMode() != mode)
			MapEditor.execute(() -> editor.new ChangeMode(mode));
	}

	/** Selects the texture with this name, or none, for null. */
	public void chooseTexture(String name)
	{
		for (ModelTexture texture : TextureManager.textureList) {
			if (texture.getName().equals(name)) {
				editor.setSelectedTexture(texture);
				return;
			}
		}
		editor.setSelectedTexture(null);
	}

	public void selectModelsWithTexture()
	{
		editor.submitGuiCommand(GuiCommand.SELECT_ALL_WITH_TEXTURE);
	}

	public void send()
	{
		JsonObject mode = new JsonObject();
		mode.addProperty("type", "mode");
		mode.addProperty("mode", editor.getEditorMode().name());
		ModelTexture selected = editor.selectionManager.selectedTexture;
		if (selected != null)
			mode.addProperty("texture", selected.getName());
		String text = mode.toString();
		if (!text.equals(modeSent)) {
			modeSent = text;
			editor.remote().getSession().send(mode);
		}

		// the textures and brush change less often, and take longer to compare
		if (frame++ % 15 != 0)
			return;

		if (editor.getEditorMode() == EditorMode.VertexPaint || brushSent == null) {
			JsonObject brush = PaintManager.getBrush();
			brush.addProperty("type", "brush");
			text = brush.toString();
			if (!text.equals(brushSent)) {
				brushSent = text;
				editor.remote().getSession().send(brush);
			}
		}

		if (editor.getEditorMode() == EditorMode.Texture || texturesSent == null)
			sendTextures();
	}

	private void sendTextures()
	{
		IdentityHashMap<ModelTexture, Integer> counts = new IdentityHashMap<>();
		for (Model mdl : editor.map.modelTree) {
			if (mdl.hasMesh() && mdl.getMesh().texture != null)
				counts.merge(mdl.getMesh().texture, 1, Integer::sum);
		}

		JsonArray textures = new JsonArray();
		for (ModelTexture texture : TextureManager.textureList) {
			JsonObject json = new JsonObject();
			json.addProperty("name", texture.getName());
			json.addProperty("models", counts.getOrDefault(texture, 0));
			json.addProperty("archive", texture.archiveName);
			json.addProperty("file", AssetManager.get(AssetSubdir.MAP_TEX,
				texture.archiveName + "/" + texture.getName() + ".png").getAbsolutePath());
			textures.add(json);
		}

		String text = textures.toString();
		if (text.equals(texturesSent))
			return;
		texturesSent = text;

		// each preview is encoded once, since they don't change while the textures are loaded
		for (int i = 0; i < textures.size(); i++) {
			ModelTexture texture = TextureManager.textureList.get(i);
			textures.get(i).getAsJsonObject().addProperty("preview", previews.computeIfAbsent(texture, this::preview));
		}
		JsonObject message = new JsonObject();
		message.addProperty("type", "textures");
		message.add("textures", textures);
		editor.remote().getSession().send(message);
	}

	private String preview(ModelTexture texture)
	{
		try {
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(texture.mainPreview, "png", png);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		}
		catch (IOException e) {
			return "";
		}
	}
}
