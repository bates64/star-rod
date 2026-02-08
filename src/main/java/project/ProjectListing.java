package project;

import java.io.File;
import java.io.IOException;

import dev.kdl.parse.KdlParseException;

/**
 * Lightweight project metadata for display in the project switcher.
 * Parses the manifest for name/path but does not initialize the engine.
 */
public class ProjectListing implements Comparable<ProjectListing>
{
	private final File directory;
	private final long lastOpened;
	private final Manifest manifest;

	public ProjectListing(File path, long lastOpened) throws IOException, KdlParseException
	{
		if (!path.isDirectory())
			throw new IllegalArgumentException("Project path must be a directory: " + path);
		this.directory = path.getAbsoluteFile();
		this.lastOpened = lastOpened;
		this.manifest = new Manifest(this.directory);
	}

	public ProjectListing(File path) throws IOException, KdlParseException
	{
		this(path, System.currentTimeMillis());
	}

	public String getPath()
	{
		return directory.getPath();
	}

	public File getDirectory()
	{
		return directory;
	}

	public long getLastOpened()
	{
		return lastOpened;
	}

	public String getName()
	{
		return manifest.getName();
	}

	public Manifest getManifest()
	{
		return manifest;
	}

	@Override
	public int compareTo(ProjectListing other)
	{
		// Sort by lastOpened descending (most recent first)
		return Long.compare(other.lastOpened, this.lastOpened);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof ProjectListing other))
			return false;
		return directory.equals(other.directory);
	}

	@Override
	public int hashCode()
	{
		return directory.hashCode();
	}

	@Override
	public String toString()
	{
		return getName() + " (" + directory.getAbsolutePath() + ")";
	}
}
