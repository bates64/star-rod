package common;

import javax.swing.JPanel;

import game.map.editor.Tickable;
import game.map.editor.UpdateListener;
import game.map.editor.UpdateProvider;

public abstract class InfoPanel<T extends UpdateProvider> extends JPanel implements UpdateListener, Tickable
{
	private T data;
	private int ignoreChangeConter = 0;

	public InfoPanel(BaseEditor editor, boolean doesTick)
	{
		if (doesTick)
			editor.registerTickable(this);
	}

	public final T getData()
	{
		return data;
	}

	public final void setData(T newData)
	{
		ignoreChangeConter++;

		if (data != null)
			data.deregisterListener(this);

		data = newData;
		if (newData == null) {
			ignoreChangeConter--;
			return;
		}

		newData.registerListener(this);

		beforeSetData(newData);
		updateFields(newData, "");
		afterSetData(newData);

		ignoreChangeConter--;
	}

	@Override
	public final void update(String tag)
	{
		if (getData() != null) {
			ignoreChangeConter++;
			updateFields(getData(), tag);
			ignoreChangeConter--;
		}
	}

	public final boolean ignoreEvents()
	{
		return ignoreChangeConter > 0;
	}

	// extending classes must implement this, but should never call it directly
	public abstract void updateFields(T newData, String tag);

	// extending classes may implement this, but should never call it directly
	public void beforeSetData(T newData)
	{}

	// extending classes may implement this, but should never call it directly
	public void afterSetData(T newData)
	{}

	// extending classes may implement this if they are created as tickable
	// tickable infopanels are automatically registered with the editor
	@Override
	public void tick(double deltaTime)
	{}
}
