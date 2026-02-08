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
	 * @return List of projects, or empty list if none exist
	 */
	List<Project> getAllProjects();

	/**
	 * Adds a project to the repository or updates its timestamp if it already exists.
	 * @param project The project to add or update
	 */
	void addProject(Project project);

	/**
	 * Removes a project from the repository.
	 */
	void removeProject(Project project);

	/**
	 * Updates the last opened timestamp for a project.
     */
	void updateLastOpened(Project project);
}
