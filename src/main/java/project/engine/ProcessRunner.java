package project.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for running external processes with output streaming.
 */
public class ProcessRunner
{
	private final AtomicReference<Process> currentProcess = new AtomicReference<>();
	private final AtomicBoolean cancelled = new AtomicBoolean(false);

	/**
	 * Runs a command and streams output to the listener.
	 * @param command The command to run
	 * @param workingDir The working directory, or null to use current directory
	 * @param listener Callback for output lines
	 * @return The result of running the command
	 * @throws IOException If an I/O error occurs
	 */
	public ProcessResult run(String[] command, File workingDir, BuildOutputListener listener) throws IOException
	{
		cancelled.set(false);
		Instant startTime = Instant.now();

		ProcessBuilder pb = new ProcessBuilder(command);
		if (workingDir != null) {
			pb.directory(workingDir);
		}
		pb.redirectErrorStream(false);

		Process process = pb.start();
		currentProcess.set(process);

		Thread stdoutThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					listener.onOutput(line, false);
				}
			}
			catch (IOException e) {
				// Process was likely destroyed
			}
		}, "ProcessRunner-stdout");

		Thread stderrThread = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					listener.onOutput(line, true);
				}
			}
			catch (IOException e) {
				// Process was likely destroyed
			}
		}, "ProcessRunner-stderr");

		stdoutThread.start();
		stderrThread.start();

		try {
			int exitCode = process.waitFor();
			stdoutThread.join();
			stderrThread.join();

			Duration duration = Duration.between(startTime, Instant.now());
			return new ProcessResult(exitCode, duration, cancelled.get());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			Duration duration = Duration.between(startTime, Instant.now());
			return new ProcessResult(-1, duration, true);
		}
		finally {
			currentProcess.set(null);
		}
	}

	/**
	 * Cancels the currently running process.
	 * @return True if a process was cancelled, false if no process was running
	 */
	public boolean cancel()
	{
		Process process = currentProcess.get();
		if (process != null && process.isAlive()) {
			cancelled.set(true);
			process.destroyForcibly();
			return true;
		}
		return false;
	}

	/**
	 * Returns whether a process is currently running.
	 */
	public boolean isRunning()
	{
		Process process = currentProcess.get();
		return process != null && process.isAlive();
	}

	/**
	 * Result of running a process.
	 */
	public static class ProcessResult
	{
		private final int exitCode;
		private final Duration duration;
		private final boolean cancelled;

		public ProcessResult(int exitCode, Duration duration, boolean cancelled)
		{
			this.exitCode = exitCode;
			this.duration = duration;
			this.cancelled = cancelled;
		}

		public int getExitCode()
		{
			return exitCode;
		}

		public Duration getDuration()
		{
			return duration;
		}

		public boolean wasCancelled()
		{
			return cancelled;
		}

		public boolean isSuccess()
		{
			return !cancelled && exitCode == 0;
		}
	}
}
