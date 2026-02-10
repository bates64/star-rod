package project.engine;

import java.io.File;
import java.io.IOException;

import app.Environment;
import project.Project;
import util.Logger;
import util.Priority;

/**
 * A checkout of papermario-dx that has been built, ready for modding.
 *
 * Also maintains a bare clone, so that each Engine can be a worktree of the bare repo.
 * Engines may also be a submodule inside a project for users who want to modify the engine.
 */
public class Engine
{
	private static final String BARE_REPO_NAME = "papermario-dx.git";
	private static final String REPO_URL = "https://github.com/bates64/papermario-dx.git";
	private static final String BASEROM_PATH = "ver/us/baserom.z64";
	private static final String DUMP_PATH = "ver/us/build/star-rod-dump";

	/** If a project provides a custom engine, it should be a git repo at this directory. */
	public static final String PROJECT_ENGINE_PATH = "papermario-dx";

	private final File directory; // the worktree or submodule directory
	private final String ref;
	private final boolean isSubmodule;
	private final BuildEnvironment buildEnv;

	private Engine(File directory, String ref, boolean isSubmodule, BuildEnvironment buildEnv) throws IOException, BuildException
	{
		this.directory = directory;
		this.ref = ref;
		this.isSubmodule = isSubmodule;
		this.buildEnv = buildEnv;

		if (!isSubmodule) {
			File bareRepo = getBareRepoDir();
			if (!bareRepo.exists())
				cloneBareRepo();

			if (!directory.exists() || !isGitRepo(directory))
				createWorktree();
			else
				checkoutRef();
		}

		splitAssets(); // TODO: only call when necessary i.e. git HEAD changed
	}

	/**
	 * Resolves and sets up the engine for a project.
	 * If the engine uses a worktree, it will be cloned/checked out synchronously.
	 */
	public static Engine forProject(Project project) throws BuildException, IOException
	{
		BuildEnvironment buildEnv = createBuildEnvironment();
		String ref = project.getManifest().getEngineRef();

		// Check for submodule first
		File submoduleDir = new File(project.getDirectory(), PROJECT_ENGINE_PATH);
		if (isGitRepo(submoduleDir))
			return new Engine(submoduleDir, ref, true, buildEnv);

		// Worktree-based engine
		File engineBase = buildEnv.getEngineBaseDir();

		// Use ref as directory name so projects with the same ref share the worktree
		// Prefix with wt- to prevent collision between foo and foo/bar
		File worktreeDir = new File(engineBase, "worktrees/wt-" + ref);

		return new Engine(worktreeDir, ref, false, buildEnv);
	}

	private static BuildEnvironment createBuildEnvironment() throws BuildException
	{
		if (Environment.isWindows())
			return new WslNixOsEnvironment();
		return new NixEnvironment();
	}

	private File getBareRepoDir()
	{
		return new File(buildEnv.getEngineBaseDir(), BARE_REPO_NAME);
	}

	public File getDirectory()
	{
		return directory;
	}

	public File getBaseRom()
	{
		return new File(directory, BASEROM_PATH);
	}

	public File getDumpDir()
	{
		return new File(directory, DUMP_PATH);
	}

	public String getRef()
	{
		return ref;
	}

	public boolean isSubmodule()
	{
		return isSubmodule;
	}

	public BuildEnvironment getBuildEnvironment()
	{
		return buildEnv;
	}

	private void cloneBareRepo() throws IOException
	{
		Logger.log("Downloading engine...", Priority.MILESTONE);
		buildEnv.gitCloneBare(REPO_URL, getBareRepoDir(), BuildOutputListener.toLogger());
	}

	private void createWorktree() throws IOException
	{
		Logger.log("Setting up engine ref '" + ref + "'...", Priority.MILESTONE);

		// Ensure parent directories exist (needed for refs like foo/bar)
		File parent = directory.getParentFile();
		if (parent != null && !parent.exists())
			parent.mkdirs();

		buildEnv.gitWorktreeAdd(getBareRepoDir(), directory, ref, BuildOutputListener.toLogger());
	}

	private void checkoutRef() throws IOException
	{
		Logger.log("Updating engine...", Priority.MILESTONE);
		buildEnv.gitFetchAll(getBareRepoDir(), BuildOutputListener.toLogger());
		buildEnv.gitCheckout(directory, ref, BuildOutputListener.toLogger());
	}

	public BuildResult splitAssets() throws BuildException, IOException
	{
		Logger.log("Splitting assets from ROM...", Priority.MILESTONE);
		return buildEnv.configure(directory, BuildOutputListener.toLogger());
	}

	private static boolean isGitRepo(File dir)
	{
		return dir.isDirectory() && (new File(dir, ".git").exists() || new File(dir, "HEAD").exists());
	}
}
