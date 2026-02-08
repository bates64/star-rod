package project;

import static app.Directories.DATABASE_TEMPLATES;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import dev.kdl.parse.KdlParseException;

public class Project implements Comparable<Project>
{
	private final File directory;
	private final long lastOpened; // TODO: move this, Comparable, and compareTo to a new class
	private final Manifest manifest;

	/** Loads a project from a directory. */
	public Project(File path, long lastOpened) throws IOException, KdlParseException
	{
		if (!path.isDirectory())
			throw new IllegalArgumentException("Project path must be a directory: " + path);
		this.directory = path.getAbsoluteFile();
		this.lastOpened = lastOpened;
		this.manifest = new Manifest(this);
	}

	/** Loads a project from a directory. */
	public Project(File path) throws IOException, KdlParseException
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

	/** Creates a new project from a template. */
	public static Project create(File path, String template, String id, String name) throws IOException, KdlParseException
	{
		if (!path.exists())
			path.mkdirs();
		if (!path.isDirectory())
			throw new IllegalArgumentException("Project path must be a directory: " + path);

		// Copy entire template directory here
		File templateDir = DATABASE_TEMPLATES.file(template);
		if (!templateDir.exists())
			throw new IllegalArgumentException("Missing template: " + templateDir.getPath());
		FileUtils.copyDirectory(templateDir, path);

		// Substitute placeholders in project.kdl
		File manifestFile = new File(path, Manifest.FILENAME);
		if (manifestFile.exists()) {
			String content = FileUtils.readFileToString(manifestFile, "UTF-8");
			content = content.replace("$PROJECT_ID", id);
			content = content.replace("$PROJECT_NAME", name);
			content = content.replace("$PROJECT_DESCRIPTION", "");
			FileUtils.writeStringToFile(manifestFile, content, "UTF-8");
		}

		return new Project(path);
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
