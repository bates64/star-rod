package project;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;

import util.Logger;

/**
 * Use case class for project operations.
 * Orchestrates project repository operations and integrates with Environment.
 */
public class ProjectManager
{
	private static ProjectManager instance;

	private final ProjectRepository repository;

	private ProjectManager(ProjectRepository repository)
	{
		this.repository = repository;
	}

	/**
	 * Gets the singleton instance of ProjectManager.
	 */
	public static synchronized ProjectManager getInstance()
	{
		if (instance == null) {
			instance = new ProjectManager(new JsonProjectRepository());
		}
		return instance;
	}

	/**
	 * Gets all recent projects, sorted by last opened (most recent first).
	 * Invalid projects (non-existent paths) are automatically removed.
	 */
	public List<ProjectListing> getRecentProjects()
	{
		return repository.getAllProjects();
	}

	/**
	 * Records that a project was opened (adds or updates its timestamp).
	 */
	public void recordProjectOpened(ProjectListing listing)
	{
		repository.updateLastOpened(listing);
	}

	/**
	 * Removes a project from the recent projects list.
	 * Does NOT delete files from disk.
	 */
	public void removeFromHistory(ProjectListing listing)
	{
		repository.removeProject(listing);
	}

	/**
	 * Deletes a project from disk and removes it from the history.
	 * @return true if deletion was successful, false otherwise
	 */
	public boolean deleteFromDisk(ProjectListing listing)
	{
		File projectDir = new File(listing.getPath());

		repository.removeProject(listing);

		try {
			FileUtils.deleteDirectory(projectDir);
			return true;
		}
		catch (IOException e) {
			Logger.logError("Failed to delete project: " + e.getMessage());
			return false;
		}
	}
}
