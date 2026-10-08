package common.remote;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import util.Logger;

/**
 * A connection to another program, such as Diorama64, that shows an editor's views and sends it input, in place of the
 * editor's own window. Each message either way is a little-endian u32 length of the rest, a type byte, and then the
 * payload: a JSON object for {@link #JSON}, or for {@link #FRAME}, a u16 width and height and the views' RGBA pixels,
 * bottom row first.
 */
public class RemoteSession
{
	private volatile Predicate<JsonObject> immediateHandler = null;
	public static final byte JSON = 0;
	public static final byte FRAME = 1;

	private final Socket socket;
	private final OutputStream out;
	private final BlockingQueue<JsonObject> received = new LinkedBlockingQueue<>();
	private final BlockingQueue<byte[]> outgoing = new LinkedBlockingQueue<>();

	/** The newest frame waiting to be sent. Frames replace one another, so a slow reader only skips frames. */
	private byte[] pendingFrame = null;
	/** A frame's buffer, given back once it's been sent, for the next frame to reuse. */
	private byte[] spareFrame = null;

	private volatile boolean closed = false;

	public RemoteSession(String host, int port) throws IOException
	{
		socket = new Socket(host, port);
		socket.setTcpNoDelay(true);
		out = socket.getOutputStream();

		Thread reader = new Thread(this::read, "Remote Session Reader");
		reader.setDaemon(true);
		reader.start();

		Thread writer = new Thread(this::write, "Remote Session Writer");
		writer.setDaemon(true);
		writer.start();
	}

	public boolean isClosed()
	{
		return closed;
	}

	/**
	 * Has `handler` see each message as it's received, before it's polled for, which it takes by returning true.
	 * It's called on the session's own thread, so it can answer a thread waiting for the message.
	 */
	public void setImmediateHandler(Predicate<JsonObject> handler)
	{
		immediateHandler = handler;
	}

	/** The next message received, or null if there's none yet. */
	public JsonObject poll()
	{
		return received.poll();
	}

	public void send(JsonObject message)
	{
		byte[] json = message.toString().getBytes(StandardCharsets.UTF_8);
		byte[] bytes = new byte[5 + json.length];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
			.putInt(1 + json.length)
			.put(JSON)
			.put(json);
		outgoing.add(bytes);
	}

	/**
	 * Sends a frame of `width` by `height` RGBA pixels, which replaces one still waiting to be sent.
	 */
	public void sendFrame(int width, int height, ByteBuffer pixels)
	{
		int size = 10 + pixels.remaining();
		synchronized (this) {
			byte[] bytes = (spareFrame != null && spareFrame.length == size) ? spareFrame : new byte[size];
			spareFrame = null;
			ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(size - 4)
				.put(FRAME)
				.putShort((short) width)
				.putShort((short) height)
				.put(pixels.duplicate());
			if (pendingFrame != null)
				spareFrame = pendingFrame;
			pendingFrame = bytes;
			notifyAll();
		}
	}

	public void close()
	{
		closed = true;
		try {
			socket.close();
		}
		catch (IOException e) {
			// already closed
		}
		synchronized (this) {
			notifyAll();
		}
	}

	private void read()
	{
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
			byte[] header = new byte[4];
			while (!closed) {
				in.readFully(header);
				int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
				byte[] body = new byte[length];
				in.readFully(body);
				if (body[0] == JSON) {
					String json = new String(body, 1, length - 1, StandardCharsets.UTF_8);
					JsonObject message = JsonParser.parseString(json).getAsJsonObject();
					Predicate<JsonObject> handler = immediateHandler;
					if (handler == null || !handler.test(message))
						received.add(message);
				}
			}
		}
		catch (EOFException e) {
			// the other program closed the connection
		}
		catch (IOException | RuntimeException e) {
			if (!closed)
				Logger.printStackTrace(e);
		}
		close();
	}

	private void write()
	{
		try {
			while (!closed) {
				byte[] message = outgoing.poll();
				if (message == null) {
					synchronized (this) {
						message = pendingFrame;
						pendingFrame = null;
						if (message == null && outgoing.isEmpty()) {
							wait(100);
							continue;
						}
					}
				}
				if (message != null) {
					out.write(message);
					if (message[4] == FRAME) {
						synchronized (this) {
							spareFrame = message;
						}
					}
				}
			}
		}
		catch (IOException | InterruptedException e) {
			if (!closed)
				Logger.printStackTrace(e);
		}
		close();
	}
}
