package assets.ui;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;

import assets.AssetHandle;
import assets.AssetSubdir;
import util.Logger;

public class TexturesAsset extends AssetHandle
{
	private static final int THUMB_W = THUMBNAIL_WIDTH;
	private static final int THUMB_H = THUMBNAIL_HEIGHT;
	private static final int GAP = 2;
	private static final int ROW_HEIGHT = (THUMB_H - GAP) / 2; // two rows

	private final List<BufferedImage> textures = new ArrayList<>();

	public BufferedImage getPreview(int index)
	{
		return index < textures.size() ? textures.get(index) : null;
	}

	public TexturesAsset(AssetHandle asset)
	{
		super(asset);

		String dirName = FilenameUtils.getBaseName(asset.getName()) + "/";
		File dir = new File(asset.assetDir, AssetSubdir.MAP_TEX + dirName);

		try {
			List<File> images = new ArrayList<>();

			try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.toPath(), "*.png")) {
				for (Path file : stream) {
					if (Files.isRegularFile(file))
						images.add(file.toFile());
				}
			}

			Collections.shuffle(images);

			for (int i = 0; i < images.size(); i++) {
				BufferedImage img = readImage(images.get(i));
				if (img != null)
					textures.add(img);
			}
		}
		catch (IOException e) {
			Logger.logError("IOException while gathering previews from " + dirName);
		}
	}

	@Override
	public boolean thumbnailHasCheckerboard()
	{
		return false;
	}

	@Override
	protected Image loadThumbnail()
	{
		if (textures.isEmpty())
			return null;

		var composite = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = composite.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int x = 0;
		int y = 0;
		for (BufferedImage tex : textures) {
			float scale = (float) ROW_HEIGHT / tex.getHeight();
			int w = Math.max(1, Math.round(tex.getWidth() * scale));
			int h = ROW_HEIGHT;

			if (x + w > THUMB_W) {
				// Wrap to next row
				x = 0;
				y += ROW_HEIGHT + GAP;
				if (y + ROW_HEIGHT > THUMB_H)
					break;
			}

			g.drawImage(tex, x, y, w, h, null);
			x += w + GAP;
		}

		g.dispose();
		return composite;
	}

	private static BufferedImage readImage(File imgFile)
	{
		try {
			return ImageIO.read(imgFile);
		}
		catch (IOException e) {
			return null;
		}
	}
}
