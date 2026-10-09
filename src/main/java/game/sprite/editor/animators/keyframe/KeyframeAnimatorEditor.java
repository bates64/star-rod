package game.sprite.editor.animators.keyframe;

import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import app.SwingUtils;
import game.sprite.SpriteComponent;
import game.sprite.editor.SpriteEditor;
import game.sprite.editor.animators.AnimElement;
import game.sprite.editor.animators.AnimElementsList;
import game.sprite.editor.animators.ComponentAnimationEditor;
import game.sprite.editor.animators.keyframe.Keyframe.KeyframePanel;
import game.sprite.editor.animators.keyframe.ParentKey.ParentKeyPanel;
import game.sprite.editor.commands.CreateCommand;
import net.miginfocom.swing.MigLayout;
import util.Logger;

public class KeyframeAnimatorEditor extends ComponentAnimationEditor
{
	public static final String PANEL_LAYOUT_PROPERTIES = "ins 0 10 0 10, wrap";

	private static KeyframeAnimatorEditor instance;

	private AnimElementsList<AnimKeyframe> commandList;
	private ListDataListener commandListListener;

	private JPanel commandListPanel;
	private JPanel commandEditPanel;

	private SpriteEditor editor;
	private KeyframeAnimator animator;

	private AnimElement selected;

	public static void bind(SpriteEditor editor, KeyframeAnimator animator, Container commandListContainer, Container commandEditContainer)
	{
		instance().editor = editor;
		instance().animator = animator;

		commandListContainer.removeAll();
		commandListContainer.add(instance().commandListPanel, "grow");

		commandEditContainer.removeAll();
		commandEditContainer.add(instance().commandEditPanel, "grow");

		instance().commandList.setModel(animator.keyframes);

		animator.keyframes.removeListDataListener(instance().commandListListener);
		animator.keyframes.addListDataListener(instance().commandListListener);
	}

	public static void init()
	{
		// pre-load the instance for this class
		instance();

		// pre-load instances for panels which rely on callbacks
		KeyframePanel.instance();
		ParentKeyPanel.instance();
	}

	protected static KeyframeAnimatorEditor instance()
	{
		if (instance == null)
			instance = new KeyframeAnimatorEditor();
		return instance;
	}

	/** The list of the keyframes of the component being edited. */
	public static AnimElementsList<AnimKeyframe> list()
	{
		return instance().commandList;
	}

	protected static void repaintCommandList()
	{
		instance().commandList.repaint();
	}

	private KeyframeAnimatorEditor()
	{
		commandList = new AnimElementsList<AnimKeyframe>(this);
		commandList.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		commandListPanel = new JPanel(new MigLayout("fill, ins 0, wrap 3",
			"[grow, sg col][grow, sg col][grow, sg col]"));
		commandEditPanel = new JPanel(new MigLayout("fill, ins 0, wrap"));

		commandList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = commandList.locationToIndex(e.getPoint());
				if (row < 0)
					return;
				AnimElement elem = commandList.getModel().getElementAt(row);

				switch (e.getButton()) {
					case MouseEvent.BUTTON3: // right click
						animator.advanceTo(elem);
						commandListPanel.repaint();
						editor.updatePlaybackStatus();
						break;
				}
			}
		});

		JButton newBtn = new JButton("Keyframe");
		newBtn.addActionListener((e) -> create(new Keyframe(animator)));

		JButton repeatBtn = new JButton("Repeat");
		repeatBtn.addActionListener((e) -> create(new LoopKey(animator, (Keyframe) null, 3)));

		JButton gotoBtn = new JButton("Goto");
		gotoBtn.addActionListener((e) -> create(new GotoKey(animator, (Keyframe) null)));

		JButton parentBtn = new JButton("Parent");
		parentBtn.addActionListener((e) -> create(new ParentKey(animator, (SpriteComponent) null)));

		JButton notifyBtn = new JButton("Notify");
		notifyBtn.addActionListener((e) -> create(new NotifyKey(animator, 1)));

		JScrollPane listScrollPane = new JScrollPane(commandList);
		listScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		commandListPanel.add(SwingUtils.getLabel("Commands", 14), "growx, span");
		commandListPanel.add(listScrollPane, "grow, span, push");

		commandListPanel.add(newBtn, "growx");
		commandListPanel.add(repeatBtn, "growx");
		commandListPanel.add(gotoBtn, "growx");
		commandListPanel.add(parentBtn, "growx");
		commandListPanel.add(notifyBtn, "growx");

		commandListListener = new ListDataListener() {
			@Override
			public void contentsChanged(ListDataEvent e)
			{
				animator.resetAnimation();
			}

			@Override
			public void intervalAdded(ListDataEvent e)
			{
				animator.resetAnimation();
			}

			@Override
			public void intervalRemoved(ListDataEvent e)
			{
				animator.resetAnimation();
			}
		};
	}

	@Override
	public AnimElement getSelected()
	{
		return selected;
	}

	@Override
	public void setSelected(AnimElement elem)
	{
		if (elem == selected)
			return;

		selected = elem;

		commandEditPanel.removeAll();
		if (elem != null)
			commandEditPanel.add(elem.getPanel(), "grow, pushy");
		commandEditPanel.revalidate();
		commandEditPanel.repaint();
	}

	@Override
	public void restoreSelection(int lastSelectedIndex)
	{
		commandList.ignoreSelectionChange = true;

		// restore component selection (if valid) for new animation
		int old = lastSelectedIndex;
		if (old >= -1 && old < commandList.getModel().getSize()) {
			// note: -1 is a valid index corresponding to no selection
			commandList.setSelectedIndex(old);
			setSelected(commandList.getSelectedValue());
		}
		else if (commandList.getModel().getSize() > 0) {
			// safety condition -- remove? should not be needed if undo/redo state intact
			Logger.logfWarning("Reference to out of range component ID: %d", old);
			commandList.setSelectedIndex(0);
			setSelected(commandList.getSelectedValue());
		}
		else {
			// extra safety condition -- also remove?
			Logger.logfWarning("Reference to invalid component ID: %d", old);
			commandList.setSelectedIndex(-1);
			setSelected(null);
		}

		commandList.ignoreSelectionChange = false;
	}

	@Override
	public int getSelection()
	{
		return commandList.getSelectedIndex();
	}

	/**
	 * Adds a keyframe of a kind, "keyframe", "loop", "goto", "parent", or "notify", after the one selected, or after
	 * the others if none is, as the buttons do.
	 */
	public static void create(String kind)
	{
		KeyframeAnimator animator = instance().animator;
		if (animator == null)
			return;

		switch (kind) {
			case "keyframe" -> create(newKeyframe(animator));
			case "loop" -> create(new LoopKey(animator, (Keyframe) null, 3));
			case "goto" -> create(new GotoKey(animator, (Keyframe) null));
			case "parent" -> create(new ParentKey(animator, (SpriteComponent) null));
			case "notify" -> create(new NotifyKey(animator, 1));
		}
	}

	/**
	 * A keyframe like the one selected, or else the last, which it's added after: its duration and raster, or else 8
	 * frames, so it lasts, rather than running along with the next.
	 */
	private static Keyframe newKeyframe(KeyframeAnimator animator)
	{
		Keyframe kf = new Keyframe(animator);
		kf.duration = 8;

		Keyframe like = null;
		DefaultListModel<AnimKeyframe> model = instance().commandList.getDefaultModel();
		int from = instance().commandList.isSelectionEmpty() ? model.getSize() - 1 : instance().commandList.getSelectedIndex();
		for (int i = from; i >= 0 && like == null; i--) {
			if (model.get(i) instanceof Keyframe other && other.duration > 0)
				like = other;
		}
		if (like != null) {
			kf.duration = like.duration;
			kf.setImage = like.setImage;
			kf.img = like.img;
		}
		return kf;
	}

	/**
	 * Adds a goto, "goto", or a repeat of `count` times, "loop", to `target`, after the keyframe selected, or after
	 * the others if none is.
	 */
	public static void createLoop(String kind, Keyframe target, int count)
	{
		KeyframeAnimator animator = instance().animator;
		if (animator == null)
			return;

		create(kind.equals("goto") ? new GotoKey(animator, target) : new LoopKey(animator, target, count));
	}

	private static void create(AnimKeyframe cmd)
	{
		AnimElementsList<AnimKeyframe> list = instance().commandList;
		DefaultListModel<AnimKeyframe> model = instance().commandList.getDefaultModel();

		int pos;
		if (list.isSelectionEmpty())
			pos = model.getSize();
		else
			pos = list.getSelectedIndex() + 1;

		SpriteEditor.execute(new CreateCommand("Create " + cmd.getName(), list, cmd, pos));
	}
}
