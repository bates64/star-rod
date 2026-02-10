package project.engine;

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

	// --- Engine storage and git operations ---

	@Override
	public File getEngineBaseDir()
	{
		return new File(Environment.getUserStateDir(), "engine");
	}

	@Override
	public void gitCloneBare(String url, File targetDir, BuildOutputListener listener) throws IOException
	{
		targetDir.getParentFile().mkdirs();
		runGitCommand(null, listener, "clone", "--bare", url, targetDir.getAbsolutePath());
	}

	@Override
	public void gitWorktreeAdd(File bareRepo, File worktreeDir, String ref, BuildOutputListener listener) throws IOException
	{
		worktreeDir.getParentFile().mkdirs();
		runGitCommand(bareRepo, listener, "worktree", "add", worktreeDir.getAbsolutePath(), ref);
	}

	@Override
	public void gitFetchAll(File repo, BuildOutputListener listener) throws IOException
	{
		runGitCommand(repo, listener, "fetch", "--all");
	}

	@Override
	public void gitCheckout(File dir, String ref, BuildOutputListener listener) throws IOException
	{
		runGitCommand(dir, listener, "checkout", ref);
	}

	private void runGitCommand(File workingDir, BuildOutputListener listener, String... args) throws IOException
	{
		String[] cmd = new String[args.length + 1];
		cmd[0] = "git";
		System.arraycopy(args, 0, cmd, 1, args.length);
		ProcessRunner.ProcessResult result = runner.run(cmd, workingDir, listener);
		if (!result.isSuccess()) {
			throw new IOException("Git command failed with exit code " + result.getExitCode());
		}
	}

	// --- Baserom management ---

	@Override
	public void installBaserom(File sourceRom) throws BuildException, IOException
	{
		if (!sourceRom.exists()) {
			throw new IOException("Source ROM does not exist: " + sourceRom);
		}

		// Check if ROM is already in Nix store
		String expectedHash = "9ec6d2a5c2fca81ab86312328779fd042b5f3b920bf65df9f6b87b376883cb5b";
		String[] checkCmd = new String[] {
			"bash", "-c",
			"test -e $(nix-store --print-fixed-path sha256 " + expectedHash + " papermario.us.z64)"
		};

		ProcessRunner.ProcessResult checkResult = runner.run(checkCmd, null, BuildOutputListener.toLogger());
		if (checkResult.isSuccess()) {
			return; // Already in store
		}

		File targetRom = new File("/tmp/papermario.us.z64");

		// Copy ROM to /tmp
		java.nio.file.Files.copy(
			sourceRom.toPath(),
			targetRom.toPath(),
			java.nio.file.StandardCopyOption.REPLACE_EXISTING
		);

		// Add to Nix store
		String[] cmd = new String[] {
			"nix-store", "--add-fixed", "sha256", targetRom.getAbsolutePath()
		};

		ProcessRunner.ProcessResult result = runner.run(cmd, null, BuildOutputListener.toLogger());
		if (!result.isSuccess()) {
			throw new BuildException("Failed to add ROM to Nix store (exit code " + result.getExitCode() + ")");
		}
	}

	// --- Build operations ---

	@Override
	public String getName()
	{
		return "Nix";
	}

	@Override
	public BuildResult configure(File projectDir, BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment(projectDir);
		return runNixCommand(projectDir, "./configure", listener);
	}

	@Override
	public BuildResult build(File projectDir, BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment(projectDir);
		ProcessRunner.ProcessResult result = runNixCommandRaw(projectDir, "NINJA_STATUS='" + BuildOutputDialog.NINJA_STATUS + "' ninja", listener);

		if (result.wasCancelled()) {
			return BuildResult.cancelled(result.getDuration());
		}

		File rom = new File(projectDir, ROM_PATH);
		if (result.isSuccess() && rom.exists()) {
			return BuildResult.success(result.getExitCode(), result.getDuration(), rom);
		}
		else {
			String error = result.getExitCode() == 0 ? "ROM file not found" : "Build failed with exit code " + result.getExitCode();
			return BuildResult.failure(result.getExitCode(), result.getDuration(), error);
		}
	}

	@Override
	public BuildResult clean(File projectDir, BuildOutputListener listener) throws BuildException, IOException
	{
		validateEnvironment(projectDir);
		return runNixCommand(projectDir, "./configure --clean", listener);
	}

	@Override
	public CompletableFuture<BuildResult> buildAsync(File projectDir, BuildOutputListener listener)
	{
		return CompletableFuture.supplyAsync(() -> {
			try {
				return build(projectDir, listener);
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

	private void validateEnvironment(File projectDir) throws BuildException
	{
		// Check for nix binary
		if (!isNixInstalled()) {
			throw new BuildException(
				"Nix is not installed. Please install Nix from https://nixos.org/download.html\n" +
				"Run: curl -L https://nixos.org/nix/install | sh -s -- --daemon");
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

	private BuildResult runNixCommand(File projectDir, String command, BuildOutputListener listener) throws IOException
	{
		ProcessRunner.ProcessResult result = runNixCommandRaw(projectDir, command, listener);

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

	private ProcessRunner.ProcessResult runNixCommandRaw(File projectDir, String command, BuildOutputListener listener) throws IOException
	{
		String[] cmd = new String[] {
			"nix", "develop", "--extra-experimental-features", "nix-command flakes", "-c", "bash", "-c", command
		};

		return runner.run(cmd, projectDir, listener);
	}
}
