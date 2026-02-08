package project.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import javax.swing.JOptionPane;

import app.BuildOutputDialog;
import app.Environment;
import app.SwingUtils;
import util.Logger;

/**
 * Build environment implementation for Windows using WSL with NixOS.
 * Uses a dedicated WSL distro named "StarRod-NixOS".
 */
public class WslNixOsEnvironment implements BuildEnvironment
{
	private static final String DISTRO_NAME = "StarRod-NixOS";
	private static final String ROM_PATH = "ver/us/build/papermario.z64";
	private static final String NIXOS_WSL_RELEASE_URL =
		"https://github.com/nix-community/NixOS-WSL/releases/latest/download/nixos-wsl.tar.gz";

	private final ProcessRunner runner = new ProcessRunner();
	private boolean shutdownRegistered = false;

	private static final int MIN_WINDOWS_BUILD = 19041; // Windows 10 version 2004

	public WslNixOsEnvironment() throws BuildException
	{
		validateSystemRequirements();
		validateWslSupport();
		registerShutdownHook();
	}

	// --- Engine storage and git operations ---

	private static final String WSL_ENGINE_BASE = "/home/nixos/star-rod/engine";

	@Override
	public File getEngineBaseDir()
	{
		return new File("\\\\" + "wsl$\\" + DISTRO_NAME + WSL_ENGINE_BASE.replace('/', '\\'));
	}

	@Override
	public void gitCloneBare(String url, File targetDir, BuildOutputListener listener) throws IOException
	{
		String wslTarget = convertToWslPath(targetDir);
		runWslGitCommand(null, listener, "clone", "--bare", url, wslTarget);
	}

	@Override
	public void gitWorktreeAdd(File bareRepo, File worktreeDir, String ref, BuildOutputListener listener) throws IOException
	{
		String wslBareRepo = convertToWslPath(bareRepo);
		String wslWorktree = convertToWslPath(worktreeDir);
		runWslGitCommand(wslBareRepo, listener, "worktree", "add", wslWorktree, ref);
	}

	@Override
	public void gitFetchAll(File repo, BuildOutputListener listener) throws IOException
	{
		runWslGitCommand(convertToWslPath(repo), listener, "fetch", "--all");
	}

	@Override
	public void gitCheckout(File dir, String ref, BuildOutputListener listener) throws IOException
	{
		runWslGitCommand(convertToWslPath(dir), listener, "checkout", ref);
	}

	/**
	 * Runs a git command inside WSL.
	 * @param wslWorkingDir WSL-native path for working directory, or null
	 * @param listener Callback for output
	 * @param args git arguments (paths must already be WSL-native)
	 */
	private void runWslGitCommand(String wslWorkingDir, BuildOutputListener listener, String... args) throws IOException
	{
		int baseLen = 3; // wsl -d <distro>
		if (wslWorkingDir != null)
			baseLen += 2; // --cd <path>
		baseLen += 1; // git

		String[] cmd = new String[baseLen + args.length];
		int i = 0;
		cmd[i++] = "wsl";
		cmd[i++] = "-d";
		cmd[i++] = DISTRO_NAME;
		if (wslWorkingDir != null) {
			cmd[i++] = "--cd";
			cmd[i++] = wslWorkingDir;
		}
		cmd[i++] = "git";
		System.arraycopy(args, 0, cmd, i, args.length);

		ProcessRunner.ProcessResult result = runner.run(cmd, null, listener);
		if (!result.isSuccess()) {
			throw new IOException("WSL git command failed with exit code " + result.getExitCode());
		}
	}

	// --- Build operations ---

	@Override
	public String getName()
	{
		return "WSL NixOS";
	}

	@Override
	public BuildResult configure(File projectDir, BuildOutputListener listener) throws BuildException, IOException
	{
		ensureDistroExists(listener);
		return runWslCommand(projectDir, "./configure", listener);
	}

	@Override
	public BuildResult build(File projectDir, BuildOutputListener listener) throws BuildException, IOException
	{
		ensureDistroExists(listener);
		ProcessRunner.ProcessResult result = runWslCommandRaw(projectDir, "NINJA_STATUS='" + BuildOutputDialog.NINJA_STATUS + "' ./configure && ninja", listener);

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
		ensureDistroExists(listener);
		return runWslCommand(projectDir, "./configure --clean", listener);
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

	private void validateSystemRequirements() throws BuildException
	{
		validateWindowsVersion();
		validateVirtualizationSupport();
	}

	private void validateWindowsVersion() throws BuildException
	{
		try {
			ProcessBuilder pb = new ProcessBuilder(
				"powershell", "-NoProfile", "-Command",
				"[System.Environment]::OSVersion.Version.Build"
			);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			String output;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				output = reader.readLine();
			}

			int exitCode = process.waitFor();
			if (exitCode != 0 || output == null) {
				Logger.logWarning("Could not determine Windows build number, proceeding anyway");
				return;
			}

			int buildNumber = Integer.parseInt(output.trim());
			if (buildNumber < MIN_WINDOWS_BUILD) {
				throw new BuildException(
					"Building requires Windows 10 version 2004+ (Build 19041+) or Windows 11.\n" +
					"Your system is running Build " + buildNumber + ".\n" +
					"Please update Windows to build.");
			}
		}
		catch (NumberFormatException e) {
			Logger.logWarning("Could not parse Windows build number, proceeding anyway");
		}
		catch (IOException | InterruptedException e) {
			Logger.logWarning("Could not check Windows version: " + e.getMessage());
		}
	}

	private void validateVirtualizationSupport() throws BuildException
	{
		try {
			// Check if virtualization is enabled using PowerShell and WMI
			ProcessBuilder pb = new ProcessBuilder(
				"powershell", "-NoProfile", "-Command",
				"(Get-CimInstance -ClassName Win32_ComputerSystem).HypervisorPresent"
			);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			String output;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				output = reader.readLine();
			}

			int exitCode = process.waitFor();
			if (exitCode != 0 || output == null) {
				// Try alternative check using systeminfo
				checkVirtualizationViaSysteminfo();
				return;
			}

			output = output.trim();
			if ("True".equalsIgnoreCase(output)) {
				return;
			}

			// Hypervisor not present - check if CPU supports virtualization
			checkVirtualizationViaSysteminfo();
		}
		catch (IOException | InterruptedException e) {
			Logger.logWarning("Could not check virtualization status: " + e.getMessage());
		}
	}

	private void checkVirtualizationViaSysteminfo() throws BuildException
	{
		try {
			ProcessBuilder pb = new ProcessBuilder("systeminfo");
			pb.redirectErrorStream(true);
			Process process = pb.start();

			boolean vmMonitorExtensions = false;
			boolean virtualizationEnabled = false;
			boolean vmFirmwareEnabled = false;

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					// Look for Hyper-V requirements section
					if (line.contains("VM Monitor Mode Extensions:")) {
						vmMonitorExtensions = line.toLowerCase().contains("yes");
					}
					else if (line.contains("Virtualization Enabled In Firmware:")) {
						vmFirmwareEnabled = line.toLowerCase().contains("yes");
					}
					else if (line.contains("Second Level Address Translation:") ||
					         line.contains("Hyper-V")) {
						if (line.toLowerCase().contains("yes")) {
							virtualizationEnabled = true;
						}
					}
				}
			}

			process.waitFor();

			if (!vmMonitorExtensions) {
				throw new BuildException(
					"Your CPU does not support virtualization (VT-x/AMD-V).\n" +
					"Building requires a CPU with virtualization extensions.\n" +
					"Building is not available on your hardware.");
			}

			if (!vmFirmwareEnabled && !virtualizationEnabled) {
				throw new BuildException(
					"Virtualization is not enabled in your BIOS/UEFI settings.\n" +
					"Building requires virtualization to be enabled.\n" +
					"Please restart your computer, enter BIOS/UEFI setup,\n" +
					"and enable Intel VT-x or AMD-V virtualization.");
			}
		}
		catch (IOException | InterruptedException e) {
			Logger.logWarning("Could not verify virtualization via systeminfo: " + e.getMessage());
		}
	}

	private void validateWslSupport() throws BuildException
	{
		try {
			ProcessBuilder pb = new ProcessBuilder("wsl", "--status");
			pb.redirectErrorStream(true);
			Process process = pb.start();
			int exitCode = process.waitFor();

			if (exitCode != 0) {
				offerWslInstallation();
			}
		}
		catch (IOException e) {
			offerWslInstallation();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BuildException("Interrupted while checking WSL status");
		}
	}

	private void offerWslInstallation() throws BuildException
	{
		if (Environment.isCommandLine()) {
			throw new BuildException(
				"WSL is not installed on this system.\n" +
				"Please enable WSL by running 'wsl --install' in an administrator PowerShell.");
		}

		int choice = SwingUtils.getConfirmDialog()
			.setTitle("WSL Not Installed")
			.setMessage(
				"WSL (Windows Subsystem for Linux) is required to build.",
				"Would you like to install it now?",
				"Note: This requires administrator privileges and a restart.")
			.setMessageType(JOptionPane.QUESTION_MESSAGE)
			.setOptionsType(JOptionPane.YES_NO_OPTION)
			.choose();

		if (choice != JOptionPane.YES_OPTION) {
			throw new BuildException("WSL installation declined by user.", true);
		}

		try {
			Logger.log("Installing WSL...");

			// Run wsl --install --no-distribution which requires admin privileges
			// Using cmd /c start to trigger UAC prompt
			ProcessBuilder pb = new ProcessBuilder(
				"cmd", "/c", "start", "/wait", "wsl", "--install", "--no-distribution"
			);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					Logger.log(line);
				}
			}

			int exitCode = process.waitFor();

			if (exitCode != 0) {
				SwingUtils.getErrorDialog()
					.setTitle("WSL Installation Failed")
					.setMessage(
						"WSL installation failed (exit code " + exitCode + ").",
						"Please try running 'wsl --install' manually in an administrator PowerShell.")
					.show();
				throw new BuildException(
					"WSL installation failed. Please try running 'wsl --install' manually.");
			}

			// Installation succeeded, ask about restart
			int restartChoice = SwingUtils.getConfirmDialog()
				.setTitle("Restart Required")
				.setMessage(
					"WSL has been installed successfully.",
					"A restart is required to complete the installation.",
					"Would you like to restart your computer now?")
				.setMessageType(JOptionPane.QUESTION_MESSAGE)
				.setOptionsType(JOptionPane.YES_NO_OPTION)
				.choose();

			if (restartChoice == JOptionPane.YES_OPTION) {
				Logger.log("Restarting computer...");
				Runtime.getRuntime().exec("shutdown /r /t 0");
				throw new BuildException("Restarting computer to complete WSL installation.", true);
			}
			else {
				throw new BuildException(
					"Please restart your computer to complete WSL installation, then try again.", true);
			}
		}
		catch (IOException | InterruptedException e) {
			throw new BuildException(
				"Failed to install WSL: " + e.getMessage() + "\n" +
				"Please try running 'wsl --install' manually in an administrator PowerShell.");
		}
	}

	private void registerShutdownHook()
	{
		if (!shutdownRegistered) {
			Environment.addShutdownHook(this::terminateDistro);
			shutdownRegistered = true;
		}
	}

	private void ensureDistroExists(BuildOutputListener listener) throws BuildException, IOException
	{
		if (isDistroInstalled()) {
			return;
		}

		Logger.log("NixOS-WSL distro not found, installing...");
		listener.onOutput("Installing NixOS-WSL distro (this may take a few minutes)...", false);

		// Download NixOS-WSL tarball
		Path tempDir = Files.createTempDirectory("starrod-nixos-wsl");
		Path tarball = tempDir.resolve("nixos-wsl.tar.gz");

		try {
			listener.onOutput("Downloading NixOS-WSL...", false);
			downloadFile(NIXOS_WSL_RELEASE_URL, tarball);

			// Create install directory
			File installDir = new File(Environment.getUserStateDir(), "nixos-wsl");
			installDir.mkdirs();

			// Import the distro
			listener.onOutput("Importing WSL distro...", false);
			ProcessBuilder pb = new ProcessBuilder(
				"wsl", "--import", DISTRO_NAME, installDir.getAbsolutePath(), tarball.toString()
			);
			pb.redirectErrorStream(true);
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					listener.onOutput(line, false);
				}
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				throw new BuildException("Failed to import NixOS-WSL distro (exit code " + exitCode + ")");
			}

			listener.onOutput("NixOS-WSL distro installed successfully!", false);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BuildException("Interrupted while installing NixOS-WSL");
		}
		finally {
			// Clean up temp files
			Files.deleteIfExists(tarball);
			Files.deleteIfExists(tempDir);
		}
	}

	private boolean isDistroInstalled()
	{
		try {
			ProcessBuilder pb = new ProcessBuilder("wsl", "-l", "-q");
			pb.redirectErrorStream(true);
			Process process = pb.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					// WSL output may contain null characters
					line = line.replace("\0", "").trim();
					if (DISTRO_NAME.equals(line)) {
						return true;
					}
				}
			}

			process.waitFor();
			return false;
		}
		catch (IOException | InterruptedException e) {
			return false;
		}
	}

	private void downloadFile(String urlString, Path destination) throws IOException
	{
		URL url = URI.create(urlString).toURL();
		try (InputStream in = url.openStream()) {
			Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void terminateDistro()
	{
		if (!isDistroInstalled()) {
			return;
		}

		try {
			Logger.log("Terminating WSL distro: " + DISTRO_NAME);
			ProcessBuilder pb = new ProcessBuilder("wsl", "--terminate", DISTRO_NAME);
			pb.redirectErrorStream(true);
			Process process = pb.start();
			process.waitFor();
		}
		catch (IOException | InterruptedException e) {
			Logger.logError("Failed to terminate WSL distro: " + e.getMessage());
		}
	}

	private String convertToWslPath(File windowsPath)
	{
		String absPath = windowsPath.getAbsolutePath();
		// Convert WSL paths to local ones
		if (absPath.startsWith("\\\\wsl$\\" + DISTRO_NAME + "\\")) {
			String rest = absPath.substring(absPath.indexOf(DISTRO_NAME) + DISTRO_NAME.length() + 1).replace('\\', '/');
			return rest.replace("\\", "/");
		}
		// Convert C:\foo\bar to /mnt/c/foo/bar
		if (absPath.length() >= 2 && absPath.charAt(1) == ':') {
			char driveLetter = Character.toLowerCase(absPath.charAt(0));
			String rest = absPath.substring(2).replace('\\', '/');
			return "/mnt/" + driveLetter + rest;
		}
		return absPath.replace('\\', '/');
	}

	private BuildResult runWslCommand(File projectDir, String command, BuildOutputListener listener) throws IOException
	{
		ProcessRunner.ProcessResult result = runWslCommandRaw(projectDir, command, listener);

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

	private ProcessRunner.ProcessResult runWslCommandRaw(File projectDir, String command, BuildOutputListener listener) throws IOException
	{
		String wslPath = convertToWslPath(projectDir);

		String[] cmd = new String[] {
			"wsl", "-d", DISTRO_NAME, "--cd", wslPath,
			"nix", "develop", "-c", "bash", "-c", command
		};

		return runner.run(cmd, null, listener);
	}
}
