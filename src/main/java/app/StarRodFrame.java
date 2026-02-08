package app;

import com.formdev.flatlaf.util.SystemInfo;
import javax.swing.JFrame;

public class StarRodFrame extends JFrame
{
	public StarRodFrame()
	{
		super();

		reloadIcon();
		setTransparentTitleBar(true);
	}

	public StarRodFrame(String title)
	{
		super(title);

		reloadIcon();
		setTransparentTitleBar(true);
	}

	public void reloadIcon()
	{
		setIconImage(Environment.getDefaultIconImage());
	}

	public void setTransparentTitleBar(boolean transparent)
	{
    	if (SystemInfo.isMacFullWindowContentSupported)
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", transparent);
	}
}
