package project;

import java.io.File;
import java.util.Objects;

/**
 * Immutable data class representing a Star Rod project.
 * Stores the project path and last opened timestamp.
 */
public class Project implements Comparable<Project>
{
	private final File path;
	private final long lastOpened;

	public Project(File path, long lastOpened)
	{
		Objects.requireNonNull(path, "Project path cannot be null");
		this.path = path.getAbsoluteFile();
		this.lastOpened = lastOpened;
	}

	public Project(File path)
	{
		this(path, System.currentTimeMillis());
	}

	public File getPath()
	{
		return path;
	}

	public String getName()
	{
		return path.getName();
	}

	public long getLastOpened()
	{
		return lastOpened;
	}

	/**
	 * Creates a new Project instance with updated lastOpened timestamp.
	 */
	public Project withLastOpened(long timestamp)
	{
		return new Project(path, timestamp);
	}

	@Override
	public int compareTo(Project other)
	{
		// Sort by lastOpened descending (most recent first)
		return Long.compare(other.lastOpened, this.lastOpened);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Project other = (Project) obj;
		return path.equals(other.path);
	}

	@Override
	public int hashCode()
	{
		return path.hashCode();
	}

	@Override
	public String toString()
	{
		return getName() + " (" + path.getAbsolutePath() + ")";
	}
}
