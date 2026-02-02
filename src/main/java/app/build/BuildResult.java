package app.build;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

/**
 * Contains the result of a build operation.
 */
public class BuildResult
{
	public enum Status
	{
		SUCCESS,
		FAILURE,
		CANCELLED
	}

	private final Status status;
	private final int exitCode;
	private final Duration duration;
	private final File outputRom;
	private final String errorMessage;

	private BuildResult(Status status, int exitCode, Duration duration, File outputRom, String errorMessage)
	{
		this.status = status;
		this.exitCode = exitCode;
		this.duration = duration;
		this.outputRom = outputRom;
		this.errorMessage = errorMessage;
	}

	public static BuildResult success(int exitCode, Duration duration, File outputRom)
	{
		return new BuildResult(Status.SUCCESS, exitCode, duration, outputRom, null);
	}

	public static BuildResult failure(int exitCode, Duration duration, String errorMessage)
	{
		return new BuildResult(Status.FAILURE, exitCode, duration, null, errorMessage);
	}

	public static BuildResult cancelled(Duration duration)
	{
		return new BuildResult(Status.CANCELLED, -1, duration, null, "Build was cancelled");
	}

	public Status getStatus()
	{
		return status;
	}

	public boolean isSuccess()
	{
		return status == Status.SUCCESS;
	}

	public int getExitCode()
	{
		return exitCode;
	}

	public Duration getDuration()
	{
		return duration;
	}

	public Optional<File> getOutputRom()
	{
		return Optional.ofNullable(outputRom);
	}

	public Optional<String> getErrorMessage()
	{
		return Optional.ofNullable(errorMessage);
	}
}
