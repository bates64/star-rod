package game.sprite.editor.animators;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import game.sprite.editor.animators.command.AnimCommand;
import game.sprite.editor.animators.command.CommandAnimator;
import game.sprite.editor.animators.command.Goto;
import game.sprite.editor.animators.command.Label;
import game.sprite.editor.animators.command.Loop;
import game.sprite.editor.animators.command.SetImage;
import game.sprite.editor.animators.command.SetNotify;
import game.sprite.editor.animators.command.SetPalette;
import game.sprite.editor.animators.command.SetParent;
import game.sprite.editor.animators.command.SetPosition;
import game.sprite.editor.animators.command.SetRotation;
import game.sprite.editor.animators.command.SetScale;
import game.sprite.editor.animators.command.SetUnknown;
import game.sprite.editor.animators.command.Wait;
import game.sprite.editor.animators.keyframe.AnimKeyframe;
import game.sprite.editor.animators.keyframe.GotoKey;
import game.sprite.editor.animators.keyframe.Keyframe;
import game.sprite.editor.animators.keyframe.KeyframeAnimator;
import game.sprite.editor.animators.keyframe.LoopKey;
import game.sprite.editor.animators.keyframe.NotifyKey;
import game.sprite.editor.animators.keyframe.ParentKey;

public class ToKeyframesConverter
{
	private final CommandAnimator cmdAnim;
	private final KeyframeAnimator kfAnim;
	private final List<AnimKeyframe> keyframes;

	private Keyframe current;
	/** Whether a Label names the current keyframe, so it must be kept even if it's empty. */
	private boolean currentIsTarget;
	private int keyframeCount;

	public ToKeyframesConverter(CommandAnimator cmdAnim, KeyframeAnimator kfAnim)
	{
		this.cmdAnim = cmdAnim;
		this.kfAnim = kfAnim;

		keyframes = new ArrayList<>();
		keyframeCount = 0;

		current = new Keyframe(kfAnim);
		current.name = "";

		IdentityHashMap<Label, Keyframe> labelMap = new IdentityHashMap<>();
		ArrayList<GotoKey> gotos = new ArrayList<>();
		ArrayList<LoopKey> loops = new ArrayList<>();

		// where each label is in the commands, to tell whether a goto's fixed position is one
		IdentityHashMap<Label, Integer> labelPositions = new IdentityHashMap<>();
		int position = 0;
		for (AnimCommand cmd : cmdAnim.commands) {
			if (cmd instanceof Label lbl)
				labelPositions.put(lbl, position);
			position += cmd.length();
		}

		for (AnimCommand cmd : cmdAnim.commands) {
			if (cmd instanceof Label lbl) {
				// the commands before the label are a keyframe of their own, without a wait
				if (!current.empty)
					advanceCurrent();
				if (current.name.isEmpty())
					current.name = lbl.name;
				labelMap.put(lbl, current);
				currentIsTarget = true;
			}
			else if (cmd instanceof Wait wait) {
				current.duration = wait.count;

				// wait command signifies end of current keyframe
				advanceCurrent();
			}
			else if (cmd instanceof Goto go2) {
				// a keyframe without a wait, as the commands before the goto run with the ones it goes to
				flushIncomplete();
				// a fixed position in the middle of a command has no label at it, only one before it
				GotoKey g2k;
				if (go2.fixedPos >= 0 && !Integer.valueOf(go2.fixedPos).equals(labelPositions.get(go2.label))) {
					g2k = new GotoKey(kfAnim, (Label) null);
					g2k.fixedPos = go2.fixedPos;
				}
				else {
					g2k = new GotoKey(kfAnim, go2.label);
				}
				gotos.add(g2k);
				keyframes.add(g2k);
			}
			else if (cmd instanceof Loop loop) {
				flushIncomplete();
				LoopKey lk;
				if (loop.fixedPos >= 0 && !Integer.valueOf(loop.fixedPos).equals(labelPositions.get(loop.label))) {
					lk = new LoopKey(kfAnim, (Label) null, loop.count);
					lk.fixedPos = loop.fixedPos;
				}
				else {
					lk = new LoopKey(kfAnim, loop.label, loop.count);
				}
				loops.add(lk);
				keyframes.add(lk);
			}
			else if (cmd instanceof SetPosition setPos) {
				current.unknown = setPos.unknown;
				current.explicitPos = true;
				current.dx = setPos.x;
				current.dy = setPos.y;
				current.dz = setPos.z;
				// even to the default, which it now sets as its command did
				current.empty = false;
			}
			else if (cmd instanceof SetRotation setRot) {
				current.explicitRot = true;
				current.rx = setRot.x;
				current.ry = setRot.y;
				current.rz = setRot.z;
				current.empty = false;
			}
			else if (cmd instanceof SetScale setScale) {
				current.explicitScale = true;
				switch (setScale.mode) {
					case UNIFORM:
						current.sx = setScale.percent;
						current.sy = setScale.percent;
						current.sz = setScale.percent;
						break;
					case X:
						current.sx = setScale.percent;
						break;
					case Y:
						current.sy = setScale.percent;
						break;
					case Z:
						current.sz = setScale.percent;
						break;
				}
				current.empty = false;
			}
			else if (cmd instanceof SetImage setImg) {
				current.img = setImg.img;
				current.setImage = true;
				current.empty = false;
				// setting the image clears the palette, so a palette set before it does nothing
				current.setPalette = false;
				current.pal = null;
			}
			else if (cmd instanceof SetPalette setPal) {
				current.pal = setPal.pal;
				current.setPalette = true;
				current.empty = false;
			}
			else if (cmd instanceof SetParent setParent) {
				// in order with the commands before it, and after a label before it
				flushIncomplete();
				keyframes.add(new ParentKey(kfAnim, setParent.parent));
			}
			else if (cmd instanceof SetNotify setNotify) {
				flushIncomplete();
				keyframes.add(new NotifyKey(kfAnim, setNotify.value));
			}
			else if (cmd instanceof SetUnknown) {
				// unsupported
			}
		}

		flushIncomplete();

		for (GotoKey go2 : gotos) {
			go2.target = labelMap.get(go2.label);
		}

		for (LoopKey loop : loops) {
			loop.target = labelMap.get(loop.label);
		}
	}

	/**
	 * Ends the current keyframe without a wait, if it has commands, or a label names it, as a keyframe of no duration
	 * is: its commands run along with the ones after it.
	 */
	private void flushIncomplete()
	{
		if (!current.empty || currentIsTarget)
			advanceCurrent();
	}

	private void advanceCurrent()
	{
		keyframes.add(current);
		currentIsTarget = false;

		keyframeCount++;
		if (current.name.isEmpty()) {
			current.name = "Keyframe " + keyframeCount;
		}

		current = new Keyframe(kfAnim);
		current.name = "";
	}

	public List<AnimKeyframe> getKeyframes()
	{
		return keyframes;
	}
}
