package project;

import java.io.File;
import java.util.List;

/**
 * Interface for project persistence operations.
 */
public interface ProjectRepository
{
	/**
	 * Gets all projects sorted by last opened (most recent first).
	 * @return List of project listings, or empty list if none exist
	 */
	List<ProjectListing> getAllProjects();

	/**
	 * Adds a project to the repository or updates its timestamp if it already exists.
	 * @param listing The project listing to add or update
	 */
	void addProject(ProjectListing listing);

	/**
	 * Removes a project from the repository.
	 */
	void removeProject(ProjectListing listing);

	/**
	 * Updates the last opened timestamp for a project.
	 */
	void updateLastOpened(ProjectListing listing);
}
