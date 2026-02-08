package project.engine;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for building Paper Mario decomp projects.
 * Implementations handle different build environments (native Nix, WSL+NixOS).
 */
public interface BuildEnvironment
{
	// --- Engine storage and git operations ---

	/**
	 * Returns the base directory for engine storage (bare repo + worktrees),
	 * as a host-side File that Java can use for file operations.
	 */
	File getEngineBaseDir();

	/**
	 * Clones a bare git repository.
	 * @param url The repository URL
	 * @param targetDir Host-side File for the bare repo destination
	 * @param listener Callback for output
	 * @throws IOException If the command fails
	 */
	void gitCloneBare(String url, File targetDir, BuildOutputListener listener) throws IOException;

	/**
	 * Creates a git worktree from a bare repository.
	 * @param bareRepo Host-side File for the bare repo
	 * @param worktreeDir Host-side File for the new worktree
	 * @param ref Git ref to check out
	 * @param listener Callback for output
	 * @throws IOException If the command fails
	 */
	void gitWorktreeAdd(File bareRepo, File worktreeDir, String ref, BuildOutputListener listener) throws IOException;

	/**
	 * Fetches all remotes in a git repository.
	 * @param repo Host-side File for the repo/worktree
	 * @param listener Callback for output
	 * @throws IOException If the command fails
	 */
	void gitFetchAll(File repo, BuildOutputListener listener) throws IOException;

	/**
	 * Checks out a git ref in a repository.
	 * @param dir Host-side File for the repo/worktree
	 * @param ref Git ref to check out
	 * @param listener Callback for output
	 * @throws IOException If the command fails
	 */
	void gitCheckout(File dir, String ref, BuildOutputListener listener) throws IOException;

	// --- Build operations ---

	/**
	 * Returns a human-readable name for this environment type.
	 */
	String getName();

	/**
	 * Runs configure (./configure).
	 * @param projectDir The project directory to build in
	 * @param listener Callback for real-time build output
	 * @return The result of the configure operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult configure(File projectDir, BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Builds the project (ninja).
	 * @param projectDir The project directory to build in
	 * @param listener Callback for real-time build output
	 * @return The result of the build operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult build(File projectDir, BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Cleans the build directory (./configure --clean).
	 * @param projectDir The project directory to clean
	 * @param listener Callback for real-time build output
	 * @return The result of the clean operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult clean(File projectDir, BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Builds the project asynchronously.
	 * @param projectDir The project directory to build in
	 * @param listener Callback for real-time build output
	 * @return A CompletableFuture that completes with the build result
	 */
	CompletableFuture<BuildResult> buildAsync(File projectDir, BuildOutputListener listener);

	/**
	 * Cancels any running build operation.
	 * @return True if a build was cancelled, false if no build was running
	 */
	boolean cancel();

	/**
	 * Returns whether a build is currently in progress.
	 */
	boolean isBuilding();
}
