package game.map.scripts;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import common.commands.AbstractCommand;

import app.Environment;
import app.SwingUtils;
import game.ProjectDatabase;
import game.map.Map;
import game.map.Map.SetBackground;
import game.map.Map.ToggleBackground;
import game.map.editor.MapEditor;
import game.map.editor.UpdateListener;
import game.map.editor.ui.SwingGUI;
import game.map.editor.ui.info.marker.ColorChoicePanel;
import game.map.shading.EditableShadingData;
import game.map.shading.EditableShadingData.EditableShadingProfile;
import game.map.shading.ShadingTreePanel;
import net.miginfocom.swing.MigLayout;
import util.ui.IntTextField;
import util.ui.IntVectorPanel;
import util.ui.StringField;

public class OptionsPanel extends JPanel implements UpdateListener
{
	public static final String TAG_GENERAL = "General";
	public static final String TAG_CAMERA = "Camera";
	public static final String TAG_SHADING = "Shading";

	private boolean ignoreChanges = false;

	private Map map;

	private JCheckBox cbOverrideShape;
	private JCheckBox cbOverrideHit;
	private JCheckBox cbOverrideTex;
	private StringField overrideShapeText;
	private StringField overrideHitText;
	private StringField overrideTexText;

	private JComboBox<String> locationBox;
	private JComboBox<String> tattleBox;
	private JCheckBox cbSongVariation;
	private JComboBox<String> reverbBox;

	private JCheckBox cbHasBackground;
	private JComboBox<String> backgroundBox;

	private JCheckBox cbLeadPlayer;
	private IntTextField camVfovField;
	private IntVectorPanel camClipRange;
	private IntVectorPanel camBackgroundColor;

	private JCheckBox cbWorldFog;
	private IntVectorPanel worldFogDist;
	private IntVectorPanel worldFogColor;

	private JCheckBox cbEntityFog;
	private IntVectorPanel entityFogDist;
	private IntVectorPanel entityFogColor;

	private JButton chooseProfileButton;
	private JCheckBox cbHasShading;
	private JTextField profileNameField;

	private ColorChoicePanel shadingColorPanel;
	private IntTextField shadingOffsetField;

	private void tryExec(Supplier<AbstractCommand> factory)
	{
		if (ignoreChanges || map.features == null)
			return;
		MapEditor.execute(factory);
	}

	private void createMapComponents()
	{
		locationBox = new JComboBox<>();
		locationBox.addActionListener((e) -> {
			int index = locationBox.getSelectedIndex();
			if (index >= 0)
				tryExec(() -> map.features.locationName.mutator(Features.locationAt(map.features.locationName.get(), index)));
		});

		tattleBox = new JComboBox<>();
		tattleBox.setToolTipText("A get_tattle function in the map's code takes its place");
		tattleBox.addActionListener((e) -> {
			int index = tattleBox.getSelectedIndex();
			if (index >= 0)
				tryExec(() -> map.features.tattle.mutator(Features.tattleAt(map.features.tattle.get(), index)));
		});

		cbSongVariation = new JCheckBox(" Song Variation");
		cbSongVariation.addActionListener((e) -> {
			tryExec(() -> map.features.songVariation.mutator(cbSongVariation.isSelected()));
		});

		reverbBox = new JComboBox<>(Features.REVERB_NAMES);
		reverbBox.addActionListener((e) -> {
			tryExec(() -> map.features.sfxReverb.mutator(reverbBox.getSelectedIndex()));
		});
	}

	private void createBackgroundComponents()
	{
		cbHasBackground = new JCheckBox(" Use an image");
		cbHasBackground.addActionListener((e) -> {
			if (!ignoreChanges && cbHasBackground.isSelected() != map.hasBackground)
				MapEditor.execute(new ToggleBackground());
		});

		backgroundBox = new JComboBox<>();
		backgroundBox.addActionListener((e) -> {
			Object name = backgroundBox.getSelectedItem();
			if (ignoreChanges || name == null || name.equals(map.bgName))
				return;
			SetBackground command = SetBackground.named(name.toString());
			if (command != null)
				MapEditor.execute(command);
		});
	}

	private void createOverrideComponents()
	{
		cbOverrideShape = new JCheckBox(" Geometry");
		cbOverrideShape.addActionListener((e) -> {
			tryExec(() -> map.features.overrideShape.mutator(cbOverrideShape.isSelected()));
		});

		overrideShapeText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(() -> map.features.shapeOverrideName.mutator(s));
		});

		cbOverrideHit = new JCheckBox(" Collision");
		cbOverrideHit.addActionListener((e) -> {
			tryExec(() -> map.features.overrideHit.mutator(cbOverrideHit.isSelected()));
		});

		overrideHitText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(() -> map.features.hitOverrideName.mutator(s));
		});

		cbOverrideTex = new JCheckBox(" Textures");
		cbOverrideTex.addActionListener((e) -> {
			tryExec(() -> map.features.overrideTex.mutator(cbOverrideTex.isSelected()));
		});

		overrideTexText = new StringField(SwingConstants.LEFT, (s) -> {
			tryExec(() -> map.features.texOverrideName.mutator(s));
		});

	}

	private void createCameraComponents()
	{
		cbLeadPlayer = new JCheckBox(" Camera leads player motion");
		cbLeadPlayer.addActionListener((e) -> {
			tryExec(() -> map.features.camLeadsPlayer.mutator(cbLeadPlayer.isSelected()));
		});

		camVfovField = new IntTextField((v) -> {
			tryExec(() -> map.features.camVfov.mutator(v));
		});
		camVfovField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addVerticalBorderPadding(camVfovField);

		camClipRange = new IntVectorPanel(true, 2, (index, value) -> {
			if (ignoreChanges || map.features == null)
				return;
			if (index == 0)
				MapEditor.execute(() -> map.features.camNearClip.mutator(value));
			else if (index == 1)
				MapEditor.execute(() -> map.features.camFarClip.mutator(value));
		});

		camBackgroundColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (ignoreChanges || map.features == null)
				return;
			if (index == 0)
				MapEditor.execute(() -> map.features.bgColorR.mutator(value));
			else if (index == 1)
				MapEditor.execute(() -> map.features.bgColorG.mutator(value));
			else if (index == 2)
				MapEditor.execute(() -> map.features.bgColorB.mutator(value));
		});
	}

	private void createFogComponents()
	{
		cbWorldFog = new JCheckBox(" World Fog");
		cbWorldFog.addActionListener((e) -> {
			tryExec(() -> map.features.worldFog.enabled.mutator(cbWorldFog.isSelected()));
		});
		worldFogDist = new IntVectorPanel(true, 2, (index, value) -> {
			if (index == 0)
				tryExec(() -> map.features.worldFog.start.mutator(value));
			else if (index == 1)
				tryExec(() -> map.features.worldFog.end.mutator(value));

		});
		worldFogColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (index == 0)
				tryExec(() -> map.features.worldFog.R.mutator(value));
			else if (index == 1)
				tryExec(() -> map.features.worldFog.G.mutator(value));
			else if (index == 2)
				tryExec(() -> map.features.worldFog.B.mutator(value));

		});

		cbEntityFog = new JCheckBox(" Entity Fog");
		cbEntityFog.addActionListener((e) -> {
			tryExec(() -> map.features.entityFog.enabled.mutator(cbEntityFog.isSelected()));
		});
		entityFogDist = new IntVectorPanel(true, 2, (index, value) -> {
			if (index == 0)
				tryExec(() -> map.features.entityFog.start.mutator(value));
			else if (index == 1)
				tryExec(() -> map.features.entityFog.end.mutator(value));

		});
		entityFogColor = new IntVectorPanel(true, 3, (index, value) -> {
			if (index == 0)
				tryExec(() -> map.features.entityFog.R.mutator(value));
			else if (index == 1)
				tryExec(() -> map.features.entityFog.G.mutator(value));
			else if (index == 2)
				tryExec(() -> map.features.entityFog.B.mutator(value));

		});
	}

	private void createShadingComponents()
	{
		cbHasShading = new JCheckBox(" Sprite Shading");
		cbHasShading.addActionListener((e) -> {
			tryExec(() -> map.features.hasSpriteShading.mutator(cbHasShading.isSelected()));
		});
		cbHasShading.setVerticalAlignment(SwingConstants.CENTER);

		profileNameField = new JTextField();
		profileNameField.setEnabled(false);
		profileNameField.setEditable(false);
		profileNameField.setHorizontalAlignment(JTextField.CENTER);
		SwingUtils.addBorderPadding(profileNameField);

		chooseProfileButton = new JButton("Choose");
		chooseProfileButton.addActionListener((e) -> {
			if (ignoreChanges || map.features == null)
				return;

			String profileName = map.features.shadingProfileName.get();
			EditableShadingProfile capturedProfile = map.captureCurrentShadingProfile(profileName);
			ProjectDatabase.SpriteShading.update(profileName, capturedProfile);

			EditableShadingData workingCopy = ProjectDatabase.SpriteShading.deepCopy();

			ShadingTreePanel panel = new ShadingTreePanel(workingCopy);
			panel.setPreferredSize(new Dimension(400, 480));

			int result = SwingUtils.getOptionDialog()
				.setParent(SwingGUI.instance())
				.setCounter(SwingGUI.instance().getDialogCounter())
				.setTitle("Sprite Shading Profiles")
				.setMessage(panel)
				.setMessageType(JOptionPane.PLAIN_MESSAGE)
				.setOptionsType(JOptionPane.OK_CANCEL_OPTION)
				.setOptions("Select", "Cancel")
				.choose();

			if (result != JOptionPane.OK_OPTION)
				return;

			ProjectDatabase.SpriteShading.replaceWith(workingCopy);

			EditableShadingProfile selectedProfile = panel.getSelectedProfile();
			if (selectedProfile != null)
				map.changeShadingProfile(selectedProfile);

		});
		SwingUtils.addBorderPadding(chooseProfileButton);

		shadingColorPanel = new ColorChoicePanel(
			() -> {
				return map.features.shadingBaseColor.get();
			},
			(v) -> {
				tryExec(() -> map.features.shadingBaseColor.mutator(v));
			});

		shadingOffsetField = new IntTextField((newValue) -> {
			tryExec(() -> map.features.shadingOffset.mutator(newValue & 0xFF));
		});
		shadingOffsetField.setHorizontalAlignment(SwingConstants.CENTER);
		SwingUtils.addBorderPadding(shadingOffsetField);
	}

	public OptionsPanel()
	{
		createMapComponents();
		createBackgroundComponents();
		createOverrideComponents();
		createCameraComponents();
		createFogComponents();
		createShadingComponents();

		setLayout(new MigLayout("fillx, ins 8 8 0 8, wrap 5", "[3%][][21%][21%][21%]"));

		// a dx map borrows geometry from its code, so it has no asset overrides
		if (Environment.isDX()) {
			add(SwingUtils.getLabel("Map", 14), "span");
			add(SwingUtils.getLabel("Location", 12), "skip 1");
			add(locationBox, "span, growx");
			add(SwingUtils.getLabel("Tattle", 12), "skip 1");
			add(tattleBox, "span, growx");
			add(SwingUtils.getLabel("Reverb", 12), "skip 1");
			add(reverbBox, "span 2, growx, wrap");
			add(cbSongVariation, "skip 1, span");
		}
		else {
			add(SwingUtils.getLabel("Asset Overrides", 14), "span");

			add(cbOverrideShape, "skip 1");
			add(overrideShapeText, "span, growx");
			add(cbOverrideHit, "skip 1");
			add(overrideHitText, "span, growx");

			if (!Environment.usingTexturePool())
				add(cbOverrideTex, "skip 1, span, growx");
		}

		add(SwingUtils.getLabel("Background", 14), "gaptop 8, span");
		add(cbHasBackground, "skip 1");
		add(backgroundBox, "span, growx");
		add(SwingUtils.getLabel("Color", 12), "skip 1");
		add(camBackgroundColor, "span 3, growx, wrap");

		add(SwingUtils.getLabel("Camera Settings", 14), "gaptop 8, span");
		add(SwingUtils.getLabel("Vertical FOV", 12), "skip 1");
		add(camVfovField, "growx, wrap");
		add(SwingUtils.getLabel("Clip Planes", 12), "skip 1");
		add(camClipRange, "span 2, growx, wrap");
		add(cbLeadPlayer, "skip 1, span");

		SwingUtils.setFontSize(cbWorldFog, 14);
		add(cbWorldFog, "gaptop 8, span");

		add(SwingUtils.getLabel("Distance", 12), "skip 1");
		add(worldFogDist, "span, growx");
		add(SwingUtils.getLabel("Color", 12), "skip 1");
		add(worldFogColor, "span, growx");

		SwingUtils.setFontSize(cbEntityFog, 14);
		add(cbEntityFog, "span, gaptop 8");

		add(SwingUtils.getLabel("Distance", 12), "skip 1");
		add(entityFogDist, "span, growx");
		add(SwingUtils.getLabel("Color", 12), "skip 1");
		add(entityFogColor, "span, growx");

		if (Environment.isDX()) {
			add(SwingUtils.getLabel("Lighting", 14), "gaptop 8, span");
			add(cbHasShading, "skip 1, span");
		}
		else {
			SwingUtils.setFontSize(cbHasShading, 14);
			add(cbHasShading, "span, gaptop 8");
		}

		if (!Environment.isDX()) {
			add(SwingUtils.getLabel("Profile", 12), "skip 1");
			add(profileNameField, "span 2, growx");
			add(chooseProfileButton, "growx");
		}

		add(SwingUtils.getLabel("Base Color", 12), "skip 1");
		add(shadingColorPanel, "span, growx");

		add(SwingUtils.getLabel("Edge Offset", 12), "skip 1");
		add(shadingOffsetField, "growx");
		add(new JLabel(""), "span"); // dummy

		add(new JPanel(), "pushy");
	}

	@Override
	public void update(String tag)
	{
		MapEditor.instance().runInEDT(() -> updateFields(map.features));
	}

	public void setMap(Map m)
	{
		if (map != null)
			map.features.deregisterListener(this);

		map = m;

		if (map != null)
			map.features.registerListener(this);

		updateFields(map.features);
	}

	public void updateFields(Features features)
	{
		ignoreChanges = true;

		locationBox.setModel(new DefaultComboBoxModel<>(Features.locationOptions(features.locationName.get()).toArray(new String[0])));
		locationBox.setSelectedIndex(Features.locationIndex(features.locationName.get()));
		tattleBox.setModel(new DefaultComboBoxModel<>(Features.tattleOptions(features.tattle.get()).toArray(new String[0])));
		tattleBox.setSelectedIndex(Features.tattleIndex(features.tattle.get()));
		// only shown where the map already uses it, since a script can choose a song's variation instead
		cbSongVariation.setSelected(features.songVariation.get());
		cbSongVariation.setVisible(features.songVariation.get());
		reverbBox.setSelectedIndex(features.sfxReverb.get() & 3);

		overrideShapeText.setText(features.shapeOverrideName.get());
		overrideHitText.setText(features.hitOverrideName.get());

		cbOverrideShape.setSelected(features.overrideShape.get());
		overrideShapeText.setEnabled(features.overrideShape.get());

		cbOverrideHit.setSelected(features.overrideHit.get());
		overrideHitText.setEnabled(features.overrideHit.get());

		cbOverrideTex.setSelected(features.overrideTex.get());

		cbLeadPlayer.setSelected(features.camLeadsPlayer.get());

		camVfovField.setValue(features.camVfov.get());
		camClipRange.setValues(features.camNearClip.get(), features.camFarClip.get());
		camBackgroundColor.setValues(features.bgColorR.get(), features.bgColorG.get(), features.bgColorB.get());

		// the image covers the color, so the color only matters without one
		cbHasBackground.setSelected(map.hasBackground);
		List<String> backgrounds = new ArrayList<>(Map.getBackgroundNames());
		if (map.bgName != null && !backgrounds.contains(map.bgName))
			backgrounds.add(map.bgName);
		backgroundBox.setModel(new DefaultComboBoxModel<>(backgrounds.toArray(new String[0])));
		backgroundBox.setSelectedItem(map.bgName);
		backgroundBox.setEnabled(map.hasBackground);
		camBackgroundColor.setEnabled(!map.hasBackground);

		cbWorldFog.setSelected(features.worldFog.enabled.get());
		worldFogDist.setEnabled(features.worldFog.enabled.get());
		worldFogColor.setEnabled(features.worldFog.enabled.get());
		worldFogDist.setValues(features.worldFog.start.get(), features.worldFog.end.get());
		worldFogColor.setValues(features.worldFog.R.get(), features.worldFog.G.get(), features.worldFog.B.get());

		cbEntityFog.setSelected(features.entityFog.enabled.get());
		entityFogDist.setEnabled(features.entityFog.enabled.get());
		entityFogColor.setEnabled(features.entityFog.enabled.get());
		entityFogDist.setValues(features.entityFog.start.get(), features.entityFog.end.get());
		entityFogColor.setValues(features.entityFog.R.get(), features.entityFog.G.get(), features.entityFog.B.get());

		boolean hasShading = features.hasSpriteShading.get();
		cbHasShading.setSelected(hasShading);

		if (!Environment.isDX()) {
			profileNameField.setText(features.shadingProfileName.get());
			profileNameField.setForeground(features.hasValidShadingProfile ? null : SwingUtils.getRedTextColor());
			chooseProfileButton.setEnabled(hasShading);
		}

		shadingOffsetField.setValue(features.shadingOffset.get());
		shadingOffsetField.setEnabled(hasShading);

		shadingColorPanel.setValue(features.shadingBaseColor.get());
		shadingColorPanel.setEnabled(hasShading);

		ignoreChanges = false;
	}
}
