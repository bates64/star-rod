package common;

import java.awt.GraphicsConfiguration;
import java.awt.Window;

import util.Logger;

public abstract class GLEditor
{
	public final Object modifyLock = new Object();

	protected final EditorCanvas glCanvas;

	protected abstract void glInit();

	protected abstract void glDraw();

	protected GLEditor()
	{
		glCanvas = new EditorCanvas(this);
	}

	protected void runInContext(Runnable runnable)
	{
		glCanvas.runInContext(runnable);
	}

	/**
	 * Returns the logical width of the canvas.
	 */
	public final int glCanvasWidth()
	{
		return glCanvas.getWidth();
	}

	/**
	 * Returns the logical height of the canvas.
	 */
	public final int glCanvasHeight()
	{
		return glCanvas.getHeight();
	}

	/**
	 * Returns the physical pixel width of the canvas (for glViewport).
	 */
	public final int glCanvasPixelWidth()
	{
		return (int) (glCanvas.getWidth() * getCanvasScaleFactor());
	}

	/**
	 * Returns the physical pixel height of the canvas (for glViewport).
	 */
	public final int glCanvasPixelHeight()
	{
		return (int) (glCanvas.getHeight() * getCanvasScaleFactor());
	}

	/**
	 * Returns the HiDPI scale factor for the canvas.
	 */
	public double getCanvasScaleFactor()
	{
		GraphicsConfiguration gc = glCanvas.getGraphicsConfiguration();
		if (gc != null) {
			return gc.getDefaultTransform().getScaleX();
		}
		return 1.0;
	}

	public static void setFullScreenEnabled(Window frame, boolean b)
	{
		try {
			Class<? extends Object> fsu = Class.forName("com.apple.eawt.FullScreenUtilities");
			fsu.getMethod("setWindowCanFullScreen", Window.class, Boolean.TYPE).invoke(null, frame, b);
		}
		catch (Exception e) {
			Logger.printStackTrace(e);
		}
	}
}
