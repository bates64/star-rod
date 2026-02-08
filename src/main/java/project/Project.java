package project;

import static app.Directories.DATABASE_TEMPLATES;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import dev.kdl.parse.KdlParseException;
import project.engine.BuildException;
import project.engine.Engine;

/**
 * A fully-loaded project with an initialized engine.
 * Extends {@link ProjectListing} so it can be used anywhere a listing is expected.
 */
public class Project extends ProjectListing
{
	private final Engine engine;

	/** Loads a project from a directory, initializing the engine. */
	public Project(File path) throws IOException, KdlParseException
	{
		super(path);
		try {
			this.engine = Engine.forProject(this);
		}
		catch (BuildException e) {
			throw new IOException("Failed to initialize engine: " + e.getMessage(), e);
		}
	}

	public Engine getEngine()
	{
		return engine;
	}

	/** Creates a new project from a template. */
	public static ProjectListing create(File path, String template, String id, String name) throws IOException, KdlParseException
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
			content = content.replace("$PROJECT_ID", '"' + id + '"');
			content = content.replace("$PROJECT_NAME", '"' + name.replace('"', '\\') + '"');
			content = content.replace("$PROJECT_DESCRIPTION", '"' + "An amazing mod of Paper Mario" + '"'); // TODO: ui
			FileUtils.writeStringToFile(manifestFile, content, "UTF-8");
		}

		return new ProjectListing(path);
	}

	public void build()
	{
		// TODO
	}
}
