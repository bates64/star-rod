package game.message.editor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import app.input.IOUtils;
import app.input.InputFileException;
import app.input.Line;
import assets.AssetHandle;
import assets.AssetManager;
import game.message.Message;
import game.message.StringEncoder;
import util.Logger;

public class MessageGroup
{
	public AssetHandle asset;
	public List<Message> messages = null;
	public final int index;

	public boolean hasModified;
	public boolean hasError;

	public MessageGroup(AssetHandle asset, int index)
	{
		this.asset = asset;
		this.index = index;
		reload();
	}

	public void reload()
	{
		try {
			messages = StringEncoder.parseMessages(this);
		}
		catch (IOException e) {
			throw new InputFileException(asset, e.getMessage());
		}

		int msgIndex = 0;
		for (Message msg : messages) {
			msg.section = this.index;
			msg.index = msgIndex++;
		}
	}

	/*
	public void saveChanges()
	{
		AssetHandle saveAsset = AssetManager.getTopLevel(asset);

		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(saveAsset)) {
			for (Message msg : strings) {
				pw.println("#message:" + msg.name);
				pw.println("{");
				msg.sanitize();
				String[] lines = msg.getMarkup().split("\r?\n");
				for (String line : lines) {
					pw.println(msg.leadingTabs + line);
				}
				pw.println("}");
				pw.println("");
			}

			asset = saveAsset;
			hasModified = false;
			for (Message msg : strings) {
				msg.modified = false;
			}
		}
		catch (IOException e) {
			Logger.logError("Failed to save " + asset.getName());
			Logger.printStackTrace(e);
		}
	}
	*/

	public void saveChanges()
	{
		List<Line> linesIn;
		List<String> linesOut;

		if (!asset.exists()) {
			Logger.logError("Could not save changes to " + asset.getName());
			return;
		}

		try {
			linesIn = IOUtils.readPlainInputFile(asset);
		}
		catch (IOException e) {
			Logger.logError("Failed to save " + asset.getName());
			Logger.printStackTrace(e);
			return;
		}

		linesOut = new ArrayList<>((int) (linesIn.size() * 1.2));
		int currentLine = 0;

		//TODO ensure strings are sorted
		for (Message msg : messages) {
			if (msg.startLineNum <= 0) {
				continue;
			}

			while (currentLine < msg.startLineNum - 1) {
				linesOut.add(linesIn.get(currentLine++).str);
			}

			msg.startLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			currentLine = msg.endLineNum;
			msg.endLineNum = linesOut.size();
		}

		// flush remaining input lines
		while (currentLine < linesIn.size()) {
			linesOut.add(linesIn.get(currentLine++).str);
		}

		for (Message msg : messages) {
			if (msg.startLineNum > 0) {
				continue;
			}

			msg.startLineNum = linesOut.size() + 1;
			writeString(msg, linesOut);
			msg.endLineNum = linesOut.size();

			linesOut.add("");
		}

		AssetHandle saveAsset = AssetManager.getTopLevel(asset);
		try (PrintWriter pw = IOUtils.getBufferedPrintWriter(saveAsset)) {
			for (String line : linesOut) {
				pw.println(line);
			}

			asset = saveAsset;
			hasModified = false;
			for (Message msg : messages) {
				msg.modified = false;
			}
		}
		catch (IOException e) {
			Logger.logError("Failed to save " + asset.getName());
			Logger.printStackTrace(e);
		}
	}

	private void writeString(Message msg, List<String> linesOut)
	{
		linesOut.add("#message:" + msg.name);
		linesOut.add("{");

		msg.sanitize();
		String[] lines = msg.getMarkup().split("\r?\n");
		for (String line : lines) {
			linesOut.add(msg.leadingTabs + line);
		}

		linesOut.add("}");
		linesOut.add("");
	}
}
