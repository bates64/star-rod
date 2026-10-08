package game.map.impex;

import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import app.Environment;
import app.SwingUtils;
import game.map.MapObject;
import game.map.MapObject.MapObjectType;
import game.map.editor.MapEditor;
import game.map.editor.RemoteForm;
import game.map.impex.AssimpImporter.AssimpImportOptions;
import game.map.tree.MapObjectNode;
import net.miginfocom.swing.MigLayout;
import util.ui.FloatTextField;

public class ImportDialog extends JDialog
{
	public static final String FRAME_TITLE = "Import";

	public static enum ImportDialogResult
	{
		READY, CANCEL
	};

	public static enum ImportAsValue
	{
		Models (MapObjectType.MODEL),
		Colliders (MapObjectType.COLLIDER),
		Zones (MapObjectType.ZONE);

		private final MapObjectType type;

		private ImportAsValue(MapObjectType type)
		{
			this.type = type;
		}

		public MapObjectType getType()
		{
			return type;
		}
	};

	private static AssimpImportOptions options = new AssimpImportOptions();

	private ImportDialogResult result = ImportDialogResult.CANCEL;

	public ImportDialog(JFrame parent, File f, boolean hasDestNode)
	{
		super(parent);

		JComboBox<ImportAsValue> importAsBox = new JComboBox<>(ImportAsValue.values());
		SwingUtils.addBorderPadding(importAsBox);
		importAsBox.addActionListener((e) -> {
			options.importAs = (ImportAsValue) importAsBox.getSelectedItem();
		});

		JCheckBox cbTriangulate = new JCheckBox("Triangulate faces");
		cbTriangulate.setIconTextGap(12);
		cbTriangulate.setSelected(options.triangulate);
		cbTriangulate.addActionListener((e) -> {
			options.triangulate = cbTriangulate.isSelected();
		});
		cbTriangulate.setToolTipText("<html>"
			+ "Convert quads to triangle pairs and polygonal faces to triangle fans.<br>"
			+ "</html>");

		JCheckBox cbJoinVertices = new JCheckBox("Join identical vertices");
		cbJoinVertices.setIconTextGap(12);
		cbJoinVertices.setSelected(options.joinVertices);
		cbJoinVertices.addActionListener((e) -> {
			options.joinVertices = cbJoinVertices.isSelected();
		});
		cbJoinVertices.setToolTipText("<html>"
			+ "Merge vertices with identical positions and UV coordinates.<br>"
			+ "</html>");

		JCheckBox cbConvertUp = new JCheckBox("Convert to Y-up");
		cbConvertUp.setIconTextGap(12);
		cbConvertUp.setSelected(options.triangulate);
		cbConvertUp.addActionListener((e) -> {
			options.convertZupToYup = cbConvertUp.isSelected();
		});
		cbConvertUp.setToolTipText("<html>"
			+ "Paper Mario uses a coordinate system with Y pointing up.<br>"
			+ "This option will attempt to convert from other coordinate systems."
			+ "</html>");

		FloatTextField scaleField = new FloatTextField((val) -> options.scale = val);
		scaleField.setValue(options.scale);
		scaleField.setHorizontalAlignment(JTextField.CENTER);

		FloatTextField uvScaleField = new FloatTextField((val) -> options.uvScale = val);
		uvScaleField.setValue(options.uvScale);
		uvScaleField.setHorizontalAlignment(JTextField.CENTER);

		JButton selectButton = new JButton("Import");
		SwingUtils.addBorderPadding(selectButton);
		selectButton.addActionListener((e) -> {
			result = ImportDialogResult.READY;
			setVisible(false);
		});

		JButton cancelButton = new JButton("Cancel");
		SwingUtils.addBorderPadding(cancelButton);
		cancelButton.addActionListener((e) -> {
			setVisible(false);
		});

		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e)
			{
				setVisible(false);
			}
		});

		setLayout(new MigLayout("ins 16, fill, wrap 2, hidemode 3", "", "[]8[]"));

		if (!hasDestNode) {
			add(new JLabel("Import as"));
			add(importAsBox, "growx, gapbottom 12");
		}
		add(cbTriangulate, "span");
		add(cbJoinVertices, "span");
		add(cbConvertUp, "span, gapbottom 12");

		add(new JLabel("Unit Scale"), "w 25%");
		add(scaleField, "growx, sg field");

		add(new JLabel("UV Scale"));
		add(uvScaleField, "growx, sg field, gapbottom 12");

		add(new JPanel(), "growx, sg but, span, split 3");
		add(selectButton, "growx, sg but");
		add(cancelButton, "growx, sg but");

		pack();
		setResizable(false);

		setTitle(FRAME_TITLE);
		setIconImage(Environment.getDefaultIconImage());
		setLocationRelativeTo(parent);
		setModal(true);
	}

	/**
	 * The form another program shows in place of this dialog, which imports `f`, into `node`, if it isn't null, or
	 * else as the type of object the form chooses.
	 */
	public static RemoteForm form(MapEditor editor, File f, MapObjectNode<? extends MapObject> node)
	{
		ImportAsValue[] types = ImportAsValue.values();
		String[] typeNames = { "Models", "Colliders", "Zones" };

		RemoteForm form = new RemoteForm("Import " + f.getName(), "Import");
		if (node == null)
			form.choice("importAs", "Import as", typeNames, () -> options.importAs.ordinal(),
				i -> options.importAs = types[i]);
		form.checkbox("triangulate", "Triangulate faces", () -> options.triangulate,
			value -> options.triangulate = value);
		form.checkbox("joinVertices", "Join identical vertices", () -> options.joinVertices,
			value -> options.joinVertices = value);
		form.checkbox("convertUp", "Convert to Y-up", () -> options.convertZupToYup,
			value -> options.convertZupToYup = value);
		form.number("scale", "Unit scale", () -> options.scale, value -> options.scale = (float) value);
		form.number("uvScale", "UV scale", () -> options.uvScale, value -> options.uvScale = (float) value);
		form.onSubmit(() -> editor.map.importViaAssimp(f, options, node));
		return form;
	}

	public ImportDialogResult getResult()
	{
		return result;
	}

	public AssimpImportOptions getOptions()
	{
		return options;
	}
}
