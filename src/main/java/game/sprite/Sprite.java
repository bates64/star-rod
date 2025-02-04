package game.sprite;

import static game.sprite.SpriteKey.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Element;

import app.input.InputFileException;
import common.Vector3f;
import game.map.BoundingBox;
import game.map.shading.ShadingProfile;
import game.sprite.SpriteLoader.SpriteSet;
import game.sprite.editor.SpriteCamera;
import game.sprite.editor.SpriteCamera.BasicTraceHit;
import game.sprite.editor.SpriteCamera.BasicTraceRay;
import game.texture.Palette;
import renderer.shaders.ShaderManager;
import renderer.shaders.scene.SpriteShader;
import util.IterableListModel;
import util.Logger;
import util.xml.XmlWrapper.XmlReader;
import util.xml.XmlWrapper.XmlSerializable;
import util.xml.XmlWrapper.XmlTag;
import util.xml.XmlWrapper.XmlWriter;

public class Sprite implements XmlSerializable
{
	// const equal to (float)5/7, set at [800E1360]
	// see component matrix located in S0 at [802DC958]
	public static final float WORLD_SCALE = 0.714286f;

	public final IterableListModel<SpriteAnimation> animations = new IterableListModel<>();
	public final IterableListModel<SpriteRaster> rasters = new IterableListModel<>();
	public final IterableListModel<SpritePalette> palettes = new IterableListModel<>();

	// working set of images for the sprite rasters to reference
	//public LinkedHashMap<String, SpriteImage> images = new LinkedHashMap<>();

	public LinkedHashMap<String, ImgAsset> imgAssets = new LinkedHashMap<>();
	public LinkedHashMap<String, PalAsset> palAssets = new LinkedHashMap<>();

	private transient boolean texturesLoaded = false;
	private transient boolean readyForEditor = false;
	public transient boolean enableStencilBuffer = false;

	// create the list models and have the animators generate their animation commands
	public void prepareForEditor()
	{
		if (readyForEditor)
			return;

		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.elementAt(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.elementAt(j);
				comp.generate();
			}
		}

		recalculateIndices();

		readyForEditor = true;
	}

	public void recalculateIndices()
	{
		int idx = 0;
		for (SpritePalette pal : palettes) {
			if (pal.disabled) {
				pal.listIndex = -1;
			}
			else {
				pal.listIndex = idx++;
			}
		}

		for (int i = 0; i < rasters.size(); i++) {
			rasters.get(i).listIndex = i;
		}

		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.listIndex = i;
			for (int j = 0; j < anim.components.size(); j++)
				anim.components.get(j).listIndex = j;
		}
	}

	public void assignDefaultAnimationNames()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			anim.name = String.format("Anim_%02X", i);
			for (int j = 0; j < anim.components.size(); j++)
				anim.components.get(j).name = String.format("Comp_%02X", j);
		}
	}

	public File source;
	private final boolean isPlayerSprite;
	public boolean hasBack;

	public String name = "";
	public List<String> variationNames;
	public int numVariations;
	public int maxComponents;

	private int atlasH, atlasW;

	public transient BoundingBox aabb = new BoundingBox();

	protected Sprite(SpriteSet set)
	{
		this.isPlayerSprite = (set == SpriteSet.Player);
	}

	@Override
	public String toString()
	{
		return name.isEmpty() ? "Unnamed" : name;
	}

	public String getDirectoryName()
	{
		return source.getParentFile() + "/";
	}

	public boolean isPlayerSprite()
	{
		return isPlayerSprite;
	}

	public static Sprite readNpc(File xmlFile, String name)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Sprite spr = new Sprite(SpriteSet.Npc);
		spr.name = name;
		spr.fromXML(xmr, xmr.getRootElement());
		return spr;
	}

	public static Sprite readPlayer(File xmlFile, String name)
	{
		XmlReader xmr = new XmlReader(xmlFile);
		Sprite spr = new Sprite(SpriteSet.Player);
		spr.name = name;
		spr.fromXML(xmr, xmr.getRootElement());
		return spr;
	}

	public static class SpriteSummary
	{
		public final String name;
		public final List<String> palettes;
		public final List<String> animations;

		public SpriteSummary(String name)
		{
			this.name = name;
			palettes = new ArrayList<>();
			animations = new ArrayList<>();
		}
	}

	public static SpriteSummary readSummary(File xmlFile, String name)
	{
		SpriteSummary summary = new SpriteSummary(name);

		XmlReader xmr = new XmlReader(xmlFile);
		Element spriteElem = xmr.getRootElement();

		Element palettesElem = xmr.getUniqueRequiredTag(spriteElem, TAG_PALETTE_LIST);
		List<Element> paletteElems = xmr.getRequiredTags(palettesElem, TAG_PALETTE);
		for (int i = 0; i < paletteElems.size(); i++) {
			Element paletteElem = paletteElems.get(i);

			xmr.requiresAttribute(paletteElem, ATTR_SOURCE);
			String filename = xmr.getAttribute(paletteElem, ATTR_SOURCE);
			summary.palettes.add(FilenameUtils.removeExtension(filename));
		}

		Element animationsElem = xmr.getUniqueRequiredTag(spriteElem, TAG_ANIMATION_LIST);
		List<Element> animationElems = xmr.getTags(animationsElem, TAG_ANIMATION);
		for (Element animationElem : animationElems) {
			xmr.requiresAttribute(animationElem, ATTR_NAME);
			summary.animations.add(xmr.getAttribute(animationElem, ATTR_NAME));
		}

		return summary;
	}

	@Override
	public void fromXML(XmlReader xmr, Element spriteElem)
	{
		source = xmr.getSourceFile();

		// read root attributes

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_COMPONENTS)) {
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_COMPONENTS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_A);
			maxComponents = xmr.readHex(spriteElem, ATTR_SPRITE_A);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_NUM_VARIATIONS)) {
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_NUM_VARIATIONS);
		}
		else {
			xmr.requiresAttribute(spriteElem, ATTR_SPRITE_B);
			numVariations = xmr.readHex(spriteElem, ATTR_SPRITE_B);
		}

		if (xmr.hasAttribute(spriteElem, ATTR_SPRITE_VARIATIONS)) {
			variationNames = xmr.readStringList(spriteElem, ATTR_SPRITE_VARIATIONS);
		}

		if (isPlayerSprite && xmr.hasAttribute(spriteElem, ATTR_SPRITE_HAS_BACK)) {
			hasBack = xmr.readBoolean(spriteElem, ATTR_SPRITE_HAS_BACK);
		}

		// read palettes list

		Element palettesElem = xmr.getUniqueRequiredTag(spriteElem, TAG_PALETTE_LIST);
		List<Element> paletteElems = xmr.getRequiredTags(palettesElem, TAG_PALETTE);
		for (int i = 0; i < paletteElems.size(); i++) {
			Element paletteElem = paletteElems.get(i);
			SpritePalette pal = new SpritePalette(this);

			xmr.requiresAttribute(paletteElem, ATTR_ID);
			int id = xmr.readHex(paletteElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Palettes are out of order!");

			xmr.requiresAttribute(paletteElem, ATTR_SOURCE);
			pal.filename = xmr.getAttribute(paletteElem, ATTR_SOURCE);
			pal.filename = FilenameUtils.removeExtension(pal.filename);

			if (xmr.hasAttribute(paletteElem, ATTR_FRONT_ONLY)) {
				pal.frontOnly = xmr.readBoolean(paletteElem, ATTR_FRONT_ONLY);
			}

			palettes.addElement(pal);
		}

		// read rasters list

		Element rastersElem = xmr.getUniqueRequiredTag(spriteElem, TAG_RASTER_LIST);
		List<Element> rasterElems = xmr.getTags(rastersElem, TAG_RASTER);
		for (int i = 0; i < rasterElems.size(); i++) {
			Element rasterElem = rasterElems.get(i);
			SpriteRaster sr = new SpriteRaster(this);

			xmr.requiresAttribute(rasterElem, ATTR_ID);
			int id = xmr.readHex(rasterElem, ATTR_ID);

			if (id != i)
				throw new InputFileException(source, "Rasters are out of order!");

			if (xmr.hasAttribute(rasterElem, ATTR_NAME))
				sr.name = xmr.getAttribute(rasterElem, ATTR_NAME);

			xmr.requiresAttribute(rasterElem, ATTR_SOURCE);
			sr.front.filename = xmr.getAttribute(rasterElem, ATTR_SOURCE);

			if (sr.name.isEmpty()) {
				sr.name = FilenameUtils.removeExtension(sr.front.filename);
			}

			xmr.requiresAttribute(rasterElem, ATTR_PALETTE);
			int frontPalID = xmr.readHex(rasterElem, ATTR_PALETTE);

			sr.back.filename = "";
			int backPalID = frontPalID;

			if (hasBack) {
				if (xmr.hasAttribute(rasterElem, ATTR_BACK)) {
					sr.back.filename = xmr.getAttribute(rasterElem, ATTR_BACK);
				}
				else if (xmr.hasAttribute(rasterElem, ATTR_SPECIAL_SIZE)) {
					int[] size = xmr.readHexArray(rasterElem, ATTR_SPECIAL_SIZE, 2);
					sr.specialWidth = size[0];
					sr.specialHeight = size[1];
				}
				else {
					throw new InputFileException(source, "Raster requires 'back' or 'special' for sprite supporting back-facing");
				}

				// this can be set independently of the back filename
				if (xmr.hasAttribute(rasterElem, ATTR_BACK_PAL)) {
					backPalID = xmr.readHex(rasterElem, ATTR_BACK_PAL);
				}
			}

			if (frontPalID >= palettes.size())
				throw new InputFileException(source, "Palette is out of range for raster %02X: %X", i, frontPalID);
			sr.front.pal = palettes.get(frontPalID);

			if (hasBack) {
				if (backPalID >= palettes.size())
					throw new InputFileException(source, "Palette is out of range for raster %02X: %X", i, frontPalID);
				sr.back.pal = palettes.get(backPalID);
			}

			rasters.addElement(sr);
		}

		// read animations list

		Element animationsElem = xmr.getUniqueRequiredTag(spriteElem, TAG_ANIMATION_LIST);
		List<Element> animationElems = xmr.getTags(animationsElem, TAG_ANIMATION);
		for (Element animationElem : animationElems) {
			SpriteAnimation anim = new SpriteAnimation(this);
			animations.addElement(anim);

			if (xmr.hasAttribute(animationElem, ATTR_NAME))
				anim.name = xmr.getAttribute(animationElem, ATTR_NAME);

			List<Element> componentElems = xmr.getRequiredTags(animationElem, TAG_COMPONENT);
			for (Element componentElem : componentElems) {
				SpriteComponent comp = new SpriteComponent(anim);
				anim.components.addElement(comp);
				comp.fromXML(xmr, componentElem);
			}
		}
	}

	public void bindPalettes()
	{
		LinkedHashMap<String, PalAsset> remainingAssets = new LinkedHashMap<>(palAssets);

		// remove any existing SpritePalettes which have been disabled
		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.elementAt(i);
			if (sp.disabled) {
				palettes.remove(i);
				i--;
			}
		}

		// bind assets to palettes based on filename
		for (SpritePalette sp : palettes) {
			String fullName = sp.filename + PalAsset.EXT;
			PalAsset asset = remainingAssets.get(fullName);

			if (asset == null) {
				Logger.logWarning("Can't find palette: " + fullName);
				sp.asset = null;
			}
			else {
				sp.asset = asset;
				remainingAssets.remove(fullName);
			}
		}

		// add disabled 'dummy' SpritePalettes for all unused PalAssets
		for (Entry<String, PalAsset> entry : remainingAssets.entrySet()) {
			String name = FilenameUtils.removeExtension(entry.getKey());
			PalAsset pa = entry.getValue();

			SpritePalette dummyPalette = SpritePalette.createDummy(this, pa, name);
			palettes.addElement(dummyPalette);
		}
	}

	public void bindRasters()
	{
		for (SpriteRaster sr : rasters) {
			sr.bindRasters(imgAssets);
		}
	}

	protected void assignRasterPalettes()
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.boundPal = null;
		}

		// bind default palettes for images
		for (SpriteRaster sr : rasters) {
			ImgAsset front = sr.getFront();
			if (front != null) {
				front.boundPal = sr.front.pal;
			}

			ImgAsset back = sr.getBack();
			if (back != null) {
				back.boundPal = sr.back.pal;
			}
		}
	}

	public void savePalettes()
	{
		File dir = new File(source.getParentFile(), "palettes");

		for (SpritePalette sp : palettes) {
			sp.saveAs(new File(dir, sp.filename + PalAsset.EXT));
		}
	}

	@Override
	public void toXML(XmlWriter xmw)
	{
		recalculateIndices();

		XmlTag root = xmw.createTag(TAG_SPRITE, false);
		xmw.addHex(root, ATTR_SPRITE_NUM_COMPONENTS, maxComponents);
		xmw.addHex(root, ATTR_SPRITE_NUM_VARIATIONS, numVariations);
		xmw.openTag(root);

		XmlTag palettesTag = xmw.createTag(TAG_PALETTE_LIST, false);
		xmw.openTag(palettesTag);
		for (int i = 0; i < palettes.size(); i++) {
			SpritePalette sp = palettes.get(i);

			if (sp.disabled) {
				continue;
			}

			XmlTag paletteTag = xmw.createTag(TAG_PALETTE, true);
			xmw.addHex(paletteTag, ATTR_ID, i);

			xmw.addAttribute(paletteTag, ATTR_SOURCE, sp.filename + PalAsset.EXT);

			if (sp.frontOnly) {
				xmw.addBoolean(paletteTag, ATTR_FRONT_ONLY, true);
			}

			xmw.printTag(paletteTag);
		}
		xmw.closeTag(palettesTag);

		XmlTag rastersTag = xmw.createTag(TAG_RASTER_LIST, false);
		xmw.openTag(rastersTag);
		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);
			XmlTag rasterTag = xmw.createTag(TAG_RASTER, true);
			xmw.addHex(rasterTag, ATTR_ID, i);

			if (!sr.name.isEmpty())
				xmw.addAttribute(rasterTag, ATTR_NAME, sr.name);

			xmw.addHex(rasterTag, ATTR_PALETTE, sr.front.pal.getIndex());
			xmw.addAttribute(rasterTag, ATTR_SOURCE, sr.front.filename);

			if (hasBack) {
				if (!sr.back.filename.isBlank()) {
					xmw.addAttribute(rasterTag, ATTR_BACK, sr.back.filename);
				}
				else {
					xmw.addHexArray(rasterTag, ATTR_SPECIAL_SIZE, (sr.specialWidth & 0xFF), (sr.specialHeight & 0xFF));
				}

				if (sr.back.pal != sr.front.pal) {
					xmw.addHex(rasterTag, ATTR_PALETTE, sr.back.pal.getIndex());
				}
			}

			xmw.printTag(rasterTag);
		}
		xmw.closeTag(rastersTag);

		XmlTag animationsTag = xmw.createTag(TAG_ANIMATION_LIST, false);
		xmw.openTag(animationsTag);
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.elementAt(i);
			XmlTag animationTag = xmw.createTag(TAG_ANIMATION, false);
			if (!anim.name.isEmpty())
				xmw.addAttribute(animationTag, ATTR_NAME, anim.name);
			xmw.openTag(animationTag);

			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent component = anim.components.elementAt(j);
				component.toXML(xmw);
			}

			xmw.closeTag(animationTag);
		}
		xmw.closeTag(animationsTag);

		xmw.closeTag(root);
	}

	public void resaveRasters() throws IOException
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.save();
		}
	}

	public void saveChanges()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.saveChanges();
			}
		}
	}

	public void convertToKeyframes()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToKeyframes();
			}
		}
	}

	public void convertToCommands()
	{
		for (int i = 0; i < animations.size(); i++) {
			SpriteAnimation anim = animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);
				comp.convertToCommands();
			}
		}
	}

	public boolean areTexturesLoaded()
	{
		return texturesLoaded;
	}

	public void loadTextures()
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.glLoad();
		}

		for (PalAsset pa : palAssets.values()) {
			pa.pal.glLoad();
		}

		for (int i = 0; i < rasters.size(); i++) {
			SpriteRaster sr = rasters.get(i);
			sr.loadEditorImages();
		}

		texturesLoaded = true;
	}

	public void unloadTextures()
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.glDelete();
		}

		for (PalAsset pa : palAssets.values()) {
			pa.pal.glDelete();
		}
	}

	public void glRefreshRasters()
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.glLoad();
		}
	}

	public void glRefreshPalettes()
	{
		for (PalAsset pa : palAssets.values()) {
			pa.pal.glReload();
		}
	}

	public int getPaletteCount()
	{
		return palettes.size();
	}

	public int lastValidPaletteID()
	{
		return palettes.size() - 1;
	}

	public int lastValidAnimationID()
	{
		return animations.size() - 1;
	}

	// update anim based on ID
	public void resetAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).reset();
	}

	// update anim based on ID
	public void updateAnimation(int animationID)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		animations.get(animationID).step();
	}

	// render based on IDs -- these are used by the map editor
	public void render(ShadingProfile spriteShading, int animationID, int paletteOverride, boolean useBack, boolean useSelectShading,
		boolean useFiltering)
	{
		if (animationID >= animations.size())
			throw new IllegalArgumentException(String.format(
				"Animation ID is out of range: %X of %X", animationID, animations.size()));

		if (paletteOverride >= palettes.size())
			throw new IllegalArgumentException(String.format(
				"Palette ID is out of range: %X of %X", paletteOverride, palettes.size()));

		render(spriteShading, animations.get(animationID), palettes.get(paletteOverride), useBack, true, useFiltering, useSelectShading);
	}

	// render based on reference
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpritePalette paletteOverride,
		boolean useBack, boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim))
			throw new IllegalArgumentException(anim + " does not belong to " + toString());

		aabb.clear();

		for (int i = 0; i < anim.components.size(); i++) {
			SpriteComponent comp = anim.components.get(i);
			comp.render(spriteShading, paletteOverride, useBack, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
			comp.addCorners(aabb);
		}
	}

	// render single component based on references
	public void render(ShadingProfile spriteShading, SpriteAnimation anim, SpriteComponent comp, SpritePalette paletteOverride,
		boolean useBack, boolean enableSelectedHighlight, boolean useSelectShading, boolean useFiltering)
	{
		if (!animations.contains(anim))
			throw new IllegalArgumentException(anim + " does not belong to " + toString());

		if (!anim.components.contains(comp))
			throw new IllegalArgumentException(comp + " does not belong to " + anim);

		aabb.clear();

		comp.render(spriteShading, paletteOverride, useBack, enableStencilBuffer, enableSelectedHighlight, useSelectShading, false, useFiltering);
		comp.addCorners(aabb);
	}

	private static final int ATLAS_TILE_PADDING = 8;
	private static final int ATLAS_SELECT_PADDING = 1;

	public void makeAtlas()
	{
		int totalWidth = ATLAS_TILE_PADDING;
		int totalHeight = ATLAS_TILE_PADDING;
		int validRasterCount = 0;

		for (ImgAsset ia : imgAssets.values()) {
			totalWidth += ATLAS_TILE_PADDING + ia.img.width;
			totalHeight += ATLAS_TILE_PADDING + ia.img.height;
			validRasterCount++;
		}

		float aspectRatio = 1.0f; // H/W
		int maxWidth = (int) Math.sqrt(totalWidth * totalHeight / (aspectRatio * validRasterCount));
		maxWidth = (maxWidth + 7) & 0xFFFFFFF8; // pad to multiple of 8

		int currentX = ATLAS_TILE_PADDING;
		int currentY = -ATLAS_TILE_PADDING;

		ArrayList<Integer> rowPosY = new ArrayList<>();
		ArrayList<Integer> rowTallest = new ArrayList<>();
		int currentRow = 0;
		rowTallest.add(0);

		for (ImgAsset ia : imgAssets.values()) {
			if (currentX + ia.img.width + ATLAS_TILE_PADDING > maxWidth) {
				// start new row
				currentY -= rowTallest.get(currentRow);
				rowPosY.add(currentY);
				rowTallest.add(0);

				// next row
				currentX = ATLAS_TILE_PADDING;
				currentY -= ATLAS_TILE_PADDING;
				currentRow++;
			}

			ia.atlasX = currentX;
			ia.atlasRow = currentRow;

			// move forward for next in the row
			currentX += ia.img.width;
			currentX += ATLAS_TILE_PADDING;

			if (ia.img.height > rowTallest.get(currentRow))
				rowTallest.set(currentRow, ia.img.height);
		}

		// finish row
		currentY -= rowTallest.get(currentRow);
		rowPosY.add(currentY);
		currentY -= ATLAS_TILE_PADDING;

		atlasW = maxWidth;
		atlasH = currentY;

		for (ImgAsset ia : imgAssets.values()) {
			ia.atlasY = rowPosY.get(ia.atlasRow) + ia.img.height;
		}

		// center the atlas
		for (ImgAsset ia : imgAssets.values()) {
			ia.atlasX -= atlasW / 2.0f;
			ia.atlasY -= atlasH / 2.0f;
		}

		// negative -> positive
		atlasH = Math.abs(atlasH);
	}

	public void centerAtlas(SpriteCamera sheetCamera, int canvasW, int canvasH)
	{
		sheetCamera.centerOn(canvasW, canvasH, 0, 0, 0, atlasW, atlasH, 0);
		sheetCamera.setMaxPos(Math.round(atlasW / 2.0f), Math.round(atlasH / 2.0f));
	}

	public ImgAsset tryAtlasPick(BasicTraceRay trace)
	{
		for (ImgAsset ia : imgAssets.values()) {
			Vector3f min = new Vector3f(
				ia.atlasX - ATLAS_SELECT_PADDING,
				ia.atlasY - ia.img.height - ATLAS_SELECT_PADDING,
				0);

			Vector3f max = new Vector3f(
				ia.atlasX + ia.img.width + ATLAS_SELECT_PADDING,
				ia.atlasY + ATLAS_SELECT_PADDING,
				0);

			BasicTraceHit hit = BasicTraceRay.getIntersection(trace, min, max);

			if (!hit.missed())
				return ia;
		}

		return null;
	}

	public void renderAtlas(ImgAsset selected, ImgAsset highlighted, SpritePalette overridePalette, boolean useFiltering)
	{
		for (ImgAsset ia : imgAssets.values()) {
			ia.inUse = false;
		}

		for (SpriteRaster sr : rasters) {
			ImgAsset front = sr.getFront();
			if (front != null) {
				front.inUse = true;
			}

			ImgAsset back = sr.getBack();
			if (back != null) {
				back.inUse = true;
			}
		}

		SpriteShader shader = ShaderManager.use(SpriteShader.class);
		shader.useFiltering.set(useFiltering);

		//	if(enableStencilBuffer)
		//	{
		//		glEnable(GL_STENCIL_TEST);
		//		glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
		//	}

		for (ImgAsset ia : imgAssets.values()) {
			//		if(enableStencilBuffer)
			//			glStencilFunc(GL_ALWAYS, i + 1, 0xFF);

			shader.selected.set(ia == selected);
			shader.highlighted.set(ia == highlighted);

			shader.alpha.set(ia.inUse ? 1.0f : 0.4f);

			Palette renderPalette;
			if (overridePalette != null && overridePalette.hasPal()) {
				renderPalette = overridePalette.getPal();
			}
			else {
				renderPalette = ia.getPalette();
			}

			ia.img.glBind(shader.texture);
			renderPalette.glBind(shader.palette);

			float x1 = ia.atlasX;
			float y1 = ia.atlasY;
			float x2 = ia.atlasX + ia.img.width;
			float y2 = ia.atlasY - ia.img.height;

			shader.setXYQuadCoords(x1, y2, x2, y1, 0); //TODO upside down?
			shader.renderQuad();
		}

		//	if(enableStencilBuffer)
		//		glDisable(GL_STENCIL_TEST);
	}

	public static void validate(Sprite spr)
	{
		for (int i = 0; i < spr.animations.size(); i++) {
			SpriteAnimation anim = spr.animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				System.out.printf("%02X.%X : ", i, j);

				Queue<Short> sequence = new LinkedList<>(comp.rawAnim);
				Queue<Short> cmdQueue = new LinkedList<>(sequence);

				List<Integer> keyFrameCmds = new ArrayList<>(16);

				while (!cmdQueue.isEmpty()) {
					int pos = sequence.size() - cmdQueue.size();
					short s = cmdQueue.poll();

					int type = (s >> 12) & 0xF;
					int extra = (s << 20) >> 20;

					keyFrameCmds.add(type);

					System.out.printf("%04X ", s);

					switch (type) {
						case 0x0: // delay
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra > 0);
							assert (extra <= 260); // longest delay = 4.333... seconds
							// assert(extra == 1 || extra % 2 == 0); false! -- delay count can be ODD! and >1
							break;
						case 0x1: // set image -- 1FFF sets to null (do not draw)
							assert (extra == -1 || (extra >= 0 && extra <= spr.rasters.size()));
							break;
						case 0x2: // goto command
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // this goto always jumps backwards
							break;
						case 0x3: // set pos
							assert (extra == 0 || extra == 1); //TODO absolute/relative position flag -- seems to do nothing...
							//		if(extra == 0)
							//			System.out.printf("<> %04X%n" , s);
							short dx = cmdQueue.poll();
							System.out.printf("%04X ", dx);
							short dy = cmdQueue.poll();
							System.out.printf("%04X ", dy);
							short dz = cmdQueue.poll();
							System.out.printf("%04X ", dz);
							break;
						case 0x4: // set angle
							assert (extra >= -180 && extra <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							break;
						case 0x5: // set scale -- extra == 3 is valid, but unused
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (extra == 0 || extra == 1 || extra == 2);
							break;
						case 0x6: // use palette -- FFF is valid, but unused
							assert (extra >= 0 && extra < spr.getPaletteCount());
							break;
						case 0x7: // loop
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							keyFrameCmds.clear();
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // always jumps backwards
							assert (s >= 0 && s < 25); // can be zero, how strange
							break;
						case 0x8: // set parent
							int parentType = (extra >> 8) & 0xF;
							int index = extra & 0xFF;

							switch (parentType) {
								case 0:
									// found only for the black ash poofs included with certain animations (monty mole, bandit, etc)
									assert (s == (short) 0x8000);
									break;
								case 1:
									assert (pos == 0);
									assert (index >= 0 && index < anim.components.size());
									break;
								case 2:
									//assert(pos == comp.sequence.size() - 2);
									System.out.printf("PARENT: %X%n", extra);
									assert (index == 1 || index == 2);
									break;
								default:
									assert (false);
							}
							break;
						default:
							throw new RuntimeException(String.format("Unknown animation command: %04X", s));
					}
				}
				System.out.println();
			}
		}
		System.out.println();
	}

	public static void validateKeyframes(Sprite spr)
	{
		for (int i = 0; i < spr.animations.size(); i++) {
			SpriteAnimation anim = spr.animations.get(i);
			for (int j = 0; j < anim.components.size(); j++) {
				SpriteComponent comp = anim.components.get(j);

				System.out.printf("%02X.%X : ", i, j);

				RawAnimation sequence = comp.animator.getCommandList();
				Queue<Short> cmdQueue = new LinkedList<>(sequence);

				while (!cmdQueue.isEmpty()) {
					int pos = sequence.size() - cmdQueue.size();
					short s = cmdQueue.poll();

					int type = (s >> 12) & 0xF;
					int extra = (s << 20) >> 20;

					System.out.printf("%04X ", s);

					switch (type) {
						case 0x0: // delay
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra > 0);
							assert (extra <= 260); // longest delay = 4.333... seconds
							break;
						case 0x1: // set image -- 1FFF sets to null (do not draw)
							assert (extra == -1 || (extra >= 0 && extra <= spr.rasters.size()));
							break;
						case 0x2: // goto command
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // this goto always jumps backwards
							break;
						case 0x3: // set pos
							assert (extra == 0 || extra == 1); //TODO absolute/relative position flag?
							//		if(extra == 0)
							//			System.out.printf("<> %04X%n" , s);
							short dx = cmdQueue.poll();
							System.out.printf("%04X ", dx);
							short dy = cmdQueue.poll();
							System.out.printf("%04X ", dy);
							short dz = cmdQueue.poll();
							System.out.printf("%04X ", dz);
							break;
						case 0x4: // set angle
							assert (extra >= -180 && extra <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (s >= -180 && s <= 180);
							break;
						case 0x5: // set scale
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							assert (extra == 0 || extra == 1 || extra == 2);
							break;
						case 0x6: // use palette
							assert (extra >= 0 && extra < spr.getPaletteCount());
							break;
						case 0x7: // loop
							s = cmdQueue.poll();
							System.out.printf("%04X ", s);
							if (!cmdQueue.isEmpty())
								System.out.print("| ");
							assert (extra < sequence.size());
							assert (extra < pos); // always jumps backwards
							assert (s >= 0 && s < 25); // can be zero, how strange
							break;
						case 0x8: // set parent
							int parentType = (extra >> 8) & 0xF;

							switch (parentType) {
								case 0:
									// found only for the black ash poofs included with certain animations (monty mole, bandit, etc)
									assert (s == (short) 0x8000);
									break;
								case 1:
									assert (pos == 0);
									break;
								case 2:
									//assert(pos == comp.sequence.size() - 2);
									break;
								default:
									assert (false);
							}
							break;
						default:
							throw new RuntimeException(String.format("Unknown animation command: %04X", s));
					}
				}
				System.out.println();

				comp.animator.generate(sequence);
			}
		}
		System.out.println();
	}
}
