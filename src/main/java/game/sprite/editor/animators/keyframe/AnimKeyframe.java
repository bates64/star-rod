package game.sprite.editor.animators.keyframe;

import java.util.List;

import game.sprite.editor.animators.AnimElement;

public abstract class AnimKeyframe extends AnimElement
{
	protected final KeyframeAnimator animator;

	protected AnimKeyframe(KeyframeAnimator animator)
	{
		super(animator.comp);

		this.animator = animator;
	}

	@Override
	public AnimKeyframe copy()
	{
		return copy(animator);
	}

	/** A copy of this, for another component's animator, `into`. */
	public abstract AnimKeyframe copy(KeyframeAnimator into);

	protected abstract int length();

	protected abstract void addTo(List<Short> seq);
}
