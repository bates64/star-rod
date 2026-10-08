package common.remote;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

import org.lwjgl.BufferUtils;

import renderer.shaders.RenderState;

/**
 * Stands in for the window's framebuffer while a {@link RemoteSession} shows the views, and reads back each frame
 * drawn to it. It has depth and stencil as the window's does, since picking reads them.
 */
public class RemoteOutput
{
	private int frameBuffer = 0;
	private int colorBuffer = 0;
	private int depthStencilBuffer = 0;
	private int width = 0;
	private int height = 0;

	private ByteBuffer pixels = null;
	private long lastChecksum = -1;
	private final CRC32C checksum = new CRC32C();

	/** Makes this the screen until {@link #end}, sized `width` by `height`. Needs a GL context. */
	public void begin(int width, int height)
	{
		width = Math.max(width, 1);
		height = Math.max(height, 1);
		if (frameBuffer == 0)
			frameBuffer = glGenFramebuffers();
		RenderState.bindFramebuffer(GL_FRAMEBUFFER, frameBuffer);

		if (width != this.width || height != this.height) {
			glDeleteRenderbuffers(colorBuffer);
			glDeleteRenderbuffers(depthStencilBuffer);

			colorBuffer = glGenRenderbuffers();
			glBindRenderbuffer(GL_RENDERBUFFER, colorBuffer);
			glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
			glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBuffer);

			depthStencilBuffer = glGenRenderbuffers();
			glBindRenderbuffer(GL_RENDERBUFFER, depthStencilBuffer);
			glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);
			glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthStencilBuffer);

			glBindRenderbuffer(GL_RENDERBUFFER, 0);
			pixels = BufferUtils.createByteBuffer(width * height * 4);
			lastChecksum = -1;
			this.width = width;
			this.height = height;
		}

		RenderState.setScreenFrameBuffer(frameBuffer);
		RenderState.setViewport(0, 0, width, height);
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
	}

	/** Reads back what was drawn since {@link #begin}, and sends it to `session` if it changed. */
	public void end(RemoteSession session)
	{
		glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBuffer);
		glReadBuffer(GL_COLOR_ATTACHMENT0);
		pixels.clear();
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

		checksum.reset();
		checksum.update(pixels.duplicate());
		long sum = checksum.getValue();
		if (sum != lastChecksum) {
			lastChecksum = sum;
			session.sendFrame(width, height, pixels);
		}
	}

	/** Sends the next frame even if it's the same as the last, such as to a view that's just been shown. */
	public void resend()
	{
		lastChecksum = -1;
	}
}
