package app.build;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for building Paper Mario decomp projects.
 * Implementations handle different build environments (native Nix, WSL+NixOS).
 */
public interface BuildEnvironment
{
	/**
	 * Returns a human-readable name for this environment type.
	 */
	String getName();

	/**
	 * Runs configure (./configure).
	 * @param listener Callback for real-time build output
	 * @return The result of the configure operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult configure(BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Builds the project (ninja).
	 * @param listener Callback for real-time build output
	 * @return The result of the build operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult build(BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Cleans the build directory (./configure --clean).
	 * @param listener Callback for real-time build output
	 * @return The result of the clean operation
	 * @throws BuildException If the build environment is not properly set up
	 * @throws IOException If an I/O error occurs
	 */
	BuildResult clean(BuildOutputListener listener) throws BuildException, IOException;

	/**
	 * Builds the project asynchronously.
	 * @param listener Callback for real-time build output
	 * @return A CompletableFuture that completes with the build result
	 */
	CompletableFuture<BuildResult> buildAsync(BuildOutputListener listener);

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
