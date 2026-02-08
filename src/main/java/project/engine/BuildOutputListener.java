package project.engine;

import util.Logger;

/**
 * Callback interface for receiving real-time build output.
 */
@FunctionalInterface
public interface BuildOutputListener
{
	/**
	 * Called when a line of output is received from the build process.
	 * @param line The output line (stdout or stderr)
	 * @param isError True if this line came from stderr
	 */
	void onOutput(String line, boolean isError);

	/**
	 * Creates a listener that logs all output to the Logger.
	 */
	static BuildOutputListener toLogger()
	{
		return (line, isError) -> {
			Logger.log(line);
		};
	}

	/**
	 * Creates a listener that discards all output.
	 */
	static BuildOutputListener silent()
	{
		return (line, isError) -> {};
	}

	/**
	 * Combines this listener with another, calling both for each output line.
	 */
	default BuildOutputListener andThen(BuildOutputListener other)
	{
		return (line, isError) -> {
			this.onOutput(line, isError);
			other.onOutput(line, isError);
		};
	}
}
