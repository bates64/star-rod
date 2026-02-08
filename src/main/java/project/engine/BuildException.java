package project.engine;

/**
 * Exception thrown when a build operation fails.
 */
public class BuildException extends Exception
{
	private static final long serialVersionUID = 1L;

	private final boolean silent;

	public BuildException(String message)
	{
		this(message, null, false);
	}

	public BuildException(String message, Throwable cause)
	{
		this(message, cause, false);
	}

	public BuildException(String message, boolean silent)
	{
		this(message, null, silent);
	}

	public BuildException(String message, Throwable cause, boolean silent)
	{
		super(message, cause);
		this.silent = silent;
	}

	/**
	 * Returns true if this exception should not be displayed to the user as an error.
	 * Used for graceful cancellations.
	 */
	public boolean isSilent()
	{
		return silent;
	}
}
