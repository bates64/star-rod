package project;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import app.Environment;
import dev.kdl.parse.KdlParseException;
import util.Logger;

/**
 * JSON-based implementation of ProjectRepository.
 * Stores projects in projects.json in the user config directory.
 */
public class JsonProjectRepository implements ProjectRepository
{
	private static final String PROJECTS_FILE = "projects.json";

	private final File projectsFile;
	private final Gson gson;

	public JsonProjectRepository()
	{
		this.projectsFile = new File(Environment.getUserStateDir(), PROJECTS_FILE);
		this.gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();
	}

	@Override
	public synchronized List<ProjectListing> getAllProjects()
	{
		List<ProjectData> dataList = loadProjectData();
		List<ProjectListing> projects = new ArrayList<>();

		// Convert to ProjectListing objects, filtering out invalid entries
		Iterator<ProjectData> iter = dataList.iterator();
		boolean modified = false;

		while (iter.hasNext()) {
			ProjectData data = iter.next();

			try {
				projects.add(new ProjectListing(new File(data.path), data.lastOpened));
			} catch (IOException | KdlParseException e) {
				Logger.logWarning("Ignoring invalid project: " + data.path);
				iter.remove();
				modified = true;
				continue;
			}
		}

		// Save if we removed any invalid entries
		if (modified) {
			saveProjectData(dataList);
		}

		// Sort by last opened (most recent first)
		Collections.sort(projects);
		return projects;
	}

	@Override
	public synchronized void addProject(ProjectListing listing)
	{
		List<ProjectData> dataList = loadProjectData();

		// Remove existing entry with same path (will be re-added with new timestamp)
		String absolutePath = listing.getPath();
		dataList.removeIf(data -> data.path.equals(absolutePath));

		// Add new entry
		ProjectData newData = new ProjectData();
		newData.path = absolutePath;
		newData.lastOpened = listing.getLastOpened();
		dataList.add(0, newData); // Add to beginning (most recent)

		saveProjectData(dataList);
	}

	@Override
	public synchronized void removeProject(ProjectListing listing)
	{
		List<ProjectData> dataList = loadProjectData();
		String absolutePath = listing.getPath();
		dataList.removeIf(data -> data.path.equals(absolutePath));
		saveProjectData(dataList);
	}

	@Override
	public synchronized void updateLastOpened(ProjectListing listing)
	{
		List<ProjectData> dataList = loadProjectData();
		String absolutePath = listing.getPath();

		for (ProjectData data : dataList) {
			if (data.path.equals(absolutePath)) {
				data.lastOpened = System.currentTimeMillis();
				saveProjectData(dataList);
				return;
			}
		}

		// Project not found, add it
		ProjectData newData = new ProjectData();
		newData.path = absolutePath;
		newData.lastOpened = System.currentTimeMillis();
		dataList.add(0, newData);
		saveProjectData(dataList);
	}

	private List<ProjectData> loadProjectData()
	{
		if (!projectsFile.exists()) {
			return new ArrayList<>();
		}

		try (FileReader reader = new FileReader(projectsFile)) {
			Type listType = new TypeToken<List<ProjectData>>() {}.getType();
			List<ProjectData> data = gson.fromJson(reader, listType);
			return data != null ? data : new ArrayList<>();
		}
		catch (IOException e) {
			Logger.logError("Failed to read projects file: " + e.getMessage());
			return new ArrayList<>();
		}
	}

	private void saveProjectData(List<ProjectData> dataList)
	{
		try {
			// Ensure parent directory exists
			projectsFile.getParentFile().mkdirs();

			try (FileWriter writer = new FileWriter(projectsFile)) {
				gson.toJson(dataList, writer);
			}
		}
		catch (IOException e) {
			Logger.logError("Failed to save projects file: " + e.getMessage());
		}
	}

	/**
	 * Internal data class for JSON serialization.
	 */
	private static class ProjectData
	{
		String path;
		long lastOpened;
	}
}
