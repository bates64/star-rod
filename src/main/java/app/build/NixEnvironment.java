package app.build;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import app.BuildOutputDialog;
import app.Environment;

/**
 * Build environment implementation for native Nix (Linux/macOS).
 * Runs commands via `nix develop -c bash -c "<command>"`.
 */
public class NixEnvironment implements BuildEnvironment
{
	private static final String ROM_PATH = "ver/us/build/papermario.z64";

	private final ProcessRunner runner = new ProcessRunner();

	@Override
	public String getName()
	{
		return "Nix";
	}

	@Override
	public BuildResult configure(BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment();
		return runNixCommand("./configure", listener);
	}

	@Override
	public BuildResult build(BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment();
		ProcessRunner.ProcessResult result = runNixCommandRaw("NINJA_STATUS='" + BuildOutputDialog.NINJA_STATUS + "' ninja", listener);

		if (result.wasCancelled()) {
			return BuildResult.cancelled(result.getDuration());
		}

		File rom = new File(Environment.getProjectDirectory(), ROM_PATH);
		if (result.isSuccess() && rom.exists()) {
			return BuildResult.success(result.getExitCode(), result.getDuration(), rom);
		}
		else {
			String error = result.getExitCode() == 0 ? "ROM file not found" : "Build failed with exit code " + result.getExitCode();
			return BuildResult.failure(result.getExitCode(), result.getDuration(), error);
		}
	}

	@Override
	public BuildResult clean(BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment();
		return runNixCommand("./configure --clean", listener);
	}

	@Override
	public CompletableFuture<BuildResult> buildAsync(BuildOutputListener listener)
	{
		return CompletableFuture.supplyAsync(() -> {
			try {
				return build(listener);
			}
			catch (BuildException | IOException e) {
				return BuildResult.failure(-1, java.time.Duration.ZERO, e.getMessage());
			}
		}, Environment.getExecutor());
	}

	@Override
	public boolean cancel()
	{
		return runner.cancel();
	}

	@Override
	public boolean isBuilding()
	{
		return runner.isRunning();
	}

	private void validateEnvironment() throws BuildException
	{
		// Check for nix binary
		if (!isNixInstalled()) {
			throw new BuildException(
				"Nix is not installed. Please install Nix from https://nixos.org/download.html\n" +
				"Run: curl -L https://nixos.org/nix/install | sh -s -- --daemon");
		}

		// Check for flake.nix in project directory
		File projectDir = Environment.getProjectDirectory();
		if (projectDir == null) {
			throw new BuildException("No project directory is set");
		}

		File flakeFile = new File(projectDir, "flake.nix");
		if (!flakeFile.exists()) {
			throw new BuildException("No flake.nix found in project directory: " + projectDir.getAbsolutePath());
		}
	}

	private boolean isNixInstalled()
	{
		try {
			ProcessBuilder pb = new ProcessBuilder("which", "nix");
			Process process = pb.start();
			int exitCode = process.waitFor();
			return exitCode == 0;
		}
		catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private BuildResult runNixCommand(String command, BuildOutputListener listener) throws IOException
	{
		ProcessRunner.ProcessResult result = runNixCommandRaw(command, listener);

		if (result.wasCancelled()) {
			return BuildResult.cancelled(result.getDuration());
		}
		else if (result.isSuccess()) {
			return BuildResult.success(result.getExitCode(), result.getDuration(), null);
		}
		else {
			return BuildResult.failure(result.getExitCode(), result.getDuration(),
				"Command failed with exit code " + result.getExitCode());
		}
	}

	private ProcessRunner.ProcessResult runNixCommandRaw(String command, BuildOutputListener listener) throws IOException
	{
		File projectDir = Environment.getProjectDirectory();

		String[] cmd = new String[] {
			"nix", "develop", "-c", "bash", "-c", command
		};

		return runner.run(cmd, projectDir, listener);
	}
}
