package game.map.editor.ui.dialogs;

import java.awt.Color;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import common.Vector3f;

import game.map.Axis;
import game.map.editor.selection.Selection;
import game.map.shape.TransformMatrix;
import net.miginfocom.swing.MigLayout;
import util.ui.LabeledDoubleSpinner;
import util.ui.LabeledIntegerSpinner;

public class TransformSelectionPanel extends JPanel implements ActionListener
{
	public enum TransformType
	{
		Translate, Rotate, Scale, Resize, Flip
	}

	private static final TransformType DEFAULT_TYPE = TransformType.Scale;

	private TransformType selectedType = TransformType.Scale;
	private final JComboBox<TransformType> typeComboBox;

	private final LabeledIntegerSpinner translateSpinnerX;
	private final LabeledIntegerSpinner translateSpinnerY;
	private final LabeledIntegerSpinner translateSpinnerZ;

	private final JLabel rotateAxisLabel;
	private final JComboBox<Axis> rotateAxisBox;
	private final LabeledDoubleSpinner rotateAngleSpinner;

	private final LabeledDoubleSpinner scaleSpinnerX;
	private final LabeledDoubleSpinner scaleSpinnerY;
	private final LabeledDoubleSpinner scaleSpinnerZ;
	private final JCheckBox uniformScaleBox;

	private final LabeledIntegerSpinner resizeSpinnerX;
	private final LabeledIntegerSpinner resizeSpinnerY;
	private final LabeledIntegerSpinner resizeSpinnerZ;

	private final JCheckBox flipCheckBoxX;
	private final JCheckBox flipCheckBoxY;
	private final JCheckBox flipCheckBoxZ;

	private int selectionSizeX;
	private int selectionSizeY;
	private int selectionSizeZ;
	private Vector3f transformOrigin = null;

	private Color RED = new Color(192, 0, 0);
	private Color GREEN = new Color(0, 160, 0);
	private Color BLUE = new Color(0, 0, 192);

	private boolean ignoreScaleChanges = false;

	public TransformSelectionPanel()
	{
		typeComboBox = new JComboBox<>(TransformType.values());
		typeComboBox.setSelectedItem(selectedType);
		typeComboBox.setActionCommand("choose_transform_type");
		typeComboBox.addActionListener(this);

		// -32768 to -32767
		translateSpinnerX = new LabeledIntegerSpinner("X", RED, -4096, 4096, 0);
		translateSpinnerY = new LabeledIntegerSpinner("Y", GREEN, -4096, 4096, 0);
		translateSpinnerZ = new LabeledIntegerSpinner("Z", BLUE, -4096, 4096, 0);

		rotateAxisLabel = new JLabel("Axis");
		rotateAxisLabel.setFont(rotateAxisLabel.getFont().deriveFont(12f));
		rotateAxisBox = new JComboBox<>(Axis.values());
		((JLabel) rotateAxisBox.getRenderer()).setHorizontalAlignment(JLabel.RIGHT);
		rotateAxisBox.setSelectedItem(Axis.Y);
		rotateAxisBox.setActionCommand("choose_rotation_axis");
		rotateAxisBox.addActionListener(this);
		rotateAngleSpinner = new LabeledDoubleSpinner("Angle", -180, 180, 0, 0.001);

		scaleSpinnerX = new LabeledDoubleSpinner("X", RED, 0, 8192, 1, 0.001);
		scaleSpinnerY = new LabeledDoubleSpinner("Y", GREEN, 0, 8192, 1, 0.001);
		scaleSpinnerZ = new LabeledDoubleSpinner("Z", BLUE, 0, 8192, 1, 0.001);
		uniformScaleBox = new JCheckBox("Uniform Rescale");
		uniformScaleBox.setHorizontalTextPosition(SwingConstants.LEFT);

		scaleSpinnerX.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerX.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerY.setValue(v);
				scaleSpinnerZ.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		scaleSpinnerY.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerY.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerX.setValue(v);
				scaleSpinnerZ.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		scaleSpinnerZ.addChangeListener((e) -> {
			if (uniformScaleBox.isSelected() && !ignoreScaleChanges) {
				double v = scaleSpinnerZ.getValue();
				ignoreScaleChanges = true;
				scaleSpinnerX.setValue(v);
				scaleSpinnerY.setValue(v);
				ignoreScaleChanges = false;
			}
		});

		resizeSpinnerX = new LabeledIntegerSpinner("X", RED, 0, 65535, 0);
		resizeSpinnerY = new LabeledIntegerSpinner("Y", GREEN, 0, 65535, 0);
		resizeSpinnerZ = new LabeledIntegerSpinner("Z", BLUE, 0, 65535, 0);

		flipCheckBoxX = new JCheckBox("Mirror X");
		flipCheckBoxY = new JCheckBox("Mirror Y");
		flipCheckBoxZ = new JCheckBox("Mirror Z");

		setLayout(new MigLayout("fill, hidemode 3"));
		add(typeComboBox, "growx, wrap, span, gapbottom 8");

		add(translateSpinnerX, "span, growx, wrap");
		add(translateSpinnerY, "span, growx, wrap");
		add(translateSpinnerZ, "span, growx, wrap");

		add(rotateAxisLabel, "pushx");
		add(rotateAxisBox, "w 72!, wrap");
		add(rotateAngleSpinner, "span, growx, wrap");

		add(scaleSpinnerX, "span, growx, wrap");
		add(scaleSpinnerY, "span, growx, wrap");
		add(scaleSpinnerZ, "span, growx, wrap");
		add(uniformScaleBox, "span, al right, wrap");

		add(resizeSpinnerX, "span, growx, wrap");
		add(resizeSpinnerY, "span, growx, wrap");
		add(resizeSpinnerZ, "span, growx, wrap");

		add(flipCheckBoxX, "span, growx, wrap");
		add(flipCheckBoxY, "span, growx, wrap");
		add(flipCheckBoxZ, "span, growx, wrap");

		translateSpinnerX.setVisible(false);
		translateSpinnerY.setVisible(false);
		translateSpinnerZ.setVisible(false);

		rotateAxisLabel.setVisible(false);
		rotateAxisBox.setVisible(false);
		rotateAngleSpinner.setVisible(false);

		scaleSpinnerX.setVisible(false);
		scaleSpinnerY.setVisible(false);
		scaleSpinnerZ.setVisible(false);
		uniformScaleBox.setVisible(false);

		resizeSpinnerX.setVisible(false);
		resizeSpinnerY.setVisible(false);
		resizeSpinnerZ.setVisible(false);

		flipCheckBoxX.setVisible(false);
		flipCheckBoxY.setVisible(false);
		flipCheckBoxZ.setVisible(false);

		setType(DEFAULT_TYPE);
	}

	@Override
	public void actionPerformed(ActionEvent e)
	{
		if (e.getActionCommand().equals("choose_transform_type")) {
			setType((TransformType) typeComboBox.getSelectedItem());
		}
	}

	public void setTransformType(TransformType newType)
	{
		// this will trigger and update from the combo box which invokes setType below
		typeComboBox.setSelectedItem(newType);
	}

	public void setSelection(Selection<?> selection)
	{
		transformOrigin = selection.getCenter();

		selectionSizeX = selection.aabb.max.getX() - selection.aabb.min.getX();
		selectionSizeY = selection.aabb.max.getY() - selection.aabb.min.getY();
		selectionSizeZ = selection.aabb.max.getZ() - selection.aabb.min.getZ();

		resizeSpinnerX.setValue(selectionSizeX);
		resizeSpinnerY.setValue(selectionSizeY);
		resizeSpinnerZ.setValue(selectionSizeZ);
	}

	private void setType(TransformType newType)
	{
		switch (selectedType) {
			case Translate:
				translateSpinnerX.setVisible(false);
				translateSpinnerY.setVisible(false);
				translateSpinnerZ.setVisible(false);
				break;
			case Rotate:
				rotateAxisLabel.setVisible(false);
				rotateAxisBox.setVisible(false);
				rotateAngleSpinner.setVisible(false);
				break;
			case Scale:
				scaleSpinnerX.setVisible(false);
				scaleSpinnerY.setVisible(false);
				scaleSpinnerZ.setVisible(false);
				uniformScaleBox.setVisible(false);
				break;
			case Resize:
				resizeSpinnerX.setVisible(false);
				resizeSpinnerY.setVisible(false);
				resizeSpinnerZ.setVisible(false);
				break;
			case Flip:
				flipCheckBoxX.setVisible(false);
				flipCheckBoxY.setVisible(false);
				flipCheckBoxZ.setVisible(false);
				break;
		}

		selectedType = newType;

		switch (selectedType) {
			case Translate:
				translateSpinnerX.setVisible(true);
				translateSpinnerY.setVisible(true);
				translateSpinnerZ.setVisible(true);
				break;
			case Rotate:
				rotateAxisLabel.setVisible(true);
				rotateAxisBox.setVisible(true);
				rotateAngleSpinner.setVisible(true);
				break;
			case Scale:
				scaleSpinnerX.setVisible(true);
				scaleSpinnerY.setVisible(true);
				scaleSpinnerZ.setVisible(true);
				uniformScaleBox.setVisible(true);
				break;
			case Resize:
				resizeSpinnerX.setVisible(true);
				resizeSpinnerY.setVisible(true);
				resizeSpinnerZ.setVisible(true);
				break;
			case Flip:
				flipCheckBoxX.setVisible(true);
				flipCheckBoxY.setVisible(true);
				flipCheckBoxZ.setVisible(true);
				break;
		}

		Window w = SwingUtilities.getWindowAncestor(this);
		if (w != null)
			w.pack();
	}

	// returns null if an identity transform is selected
	public TransformMatrix createTransformMatrix()
	{
		Transform t = new Transform();
		t.type = selectedType;
		t.origin = transformOrigin;
		t.size[0] = selectionSizeX;
		t.size[1] = selectionSizeY;
		t.size[2] = selectionSizeZ;
		t.translate[0] = translateSpinnerX.getValue();
		t.translate[1] = translateSpinnerY.getValue();
		t.translate[2] = translateSpinnerZ.getValue();
		t.axis = (Axis) rotateAxisBox.getSelectedItem();
		t.angle = rotateAngleSpinner.getValue();
		t.scale[0] = scaleSpinnerX.getValue();
		t.scale[1] = scaleSpinnerY.getValue();
		t.scale[2] = scaleSpinnerZ.getValue();
		t.resize[0] = resizeSpinnerX.getValue();
		t.resize[1] = resizeSpinnerY.getValue();
		t.resize[2] = resizeSpinnerZ.getValue();
		t.flip[0] = flipCheckBoxX.isSelected();
		t.flip[1] = flipCheckBoxY.isSelected();
		t.flip[2] = flipCheckBoxZ.isSelected();
		return t.createMatrix();
	}

	/** A transform of a selection, of any type, about its center. */
	public static class Transform
	{
		public TransformType type = DEFAULT_TYPE;
		public final int[] translate = new int[3];
		public Axis axis = Axis.Y;
		public double angle = 0;
		public final double[] scale = { 1, 1, 1 };
		public final int[] resize = new int[3];
		public final boolean[] flip = new boolean[3];

		// the selection's center, and its size, which resizing starts from
		private Vector3f origin;
		private final int[] size = new int[3];

		public void setSelection(Selection<?> selection)
		{
			origin = selection.getCenter();
			size[0] = selection.aabb.max.getX() - selection.aabb.min.getX();
			size[1] = selection.aabb.max.getY() - selection.aabb.min.getY();
			size[2] = selection.aabb.max.getZ() - selection.aabb.min.getZ();
			System.arraycopy(size, 0, resize, 0, 3);
		}

		// returns null if an identity transform is selected
		public TransformMatrix createMatrix()
		{
			TransformMatrix m = new TransformMatrix();
			double sx, sy, sz;

			switch (type) {
				case Translate:
					if (translate[0] == 0 && translate[1] == 0 && translate[2] == 0)
						return null;
					m.setTranslation(translate[0], translate[1], translate[2]);
					break;

				case Rotate:
					if (angle == 0)
						return null;
					TransformMatrix r = new TransformMatrix();
					r.setRotation(axis, angle);
					m.setTranslation(-origin.x, -origin.y, -origin.z);
					m.concat(r);
					m.translate(origin);
					break;

				case Scale:
					if (scale[0] == 1 && scale[1] == 1 && scale[2] == 1)
						return null;
					TransformMatrix scaling = new TransformMatrix();
					scaling.setScale(scale[0], scale[1], scale[2]);
					m.setTranslation(-origin.x, -origin.y, -origin.z);
					m.concat(scaling);
					m.translate(origin);
					break;

				case Resize:
					if (resize[0] == size[0] && resize[1] == size[1] && resize[2] == size[2])
						return null;
					sx = (size[0] == 0) ? 1 : (resize[0] / (double) size[0]);
					sy = (size[1] == 0) ? 1 : (resize[1] / (double) size[1]);
					sz = (size[2] == 0) ? 1 : (resize[2] / (double) size[2]);
					TransformMatrix rescale = new TransformMatrix();
					rescale.setScale(sx, sy, sz);
					m.setTranslation(-origin.x, -origin.y, -origin.z);
					m.concat(rescale);
					m.translate(origin);
					break;

				case Flip:
					if (!flip[0] && !flip[1] && !flip[2])
						return null;
					TransformMatrix mirror = new TransformMatrix();
					mirror.setScale(flip[0] ? -1 : 1, flip[1] ? -1 : 1, flip[2] ? -1 : 1);
					m.setTranslation(-origin.x, -origin.y, -origin.z);
					m.concat(mirror);
					m.translate(origin);
					break;
			}

			return m;
		}
	}
}
