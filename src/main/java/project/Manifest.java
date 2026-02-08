package project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import dev.kdl.KdlDocument;
import dev.kdl.parse.KdlParseException;
import dev.kdl.parse.KdlParser;

/** A project.kdl file. Mutations are automatically saved to disk. */
public class Manifest {
	public static final String FILENAME = "project.kdl";

	private final File file;
	private final KdlDocument doc;

	public Manifest(File directory) throws IOException, KdlParseException {
		file = new File(directory, FILENAME);

		if (!file.exists()) {
			throw new IOException(FILENAME + " does not exist");
		}

		var parser = KdlParser.v2();
		doc = parser.parse(Path.of(file.getAbsolutePath()));

		if (!hasEngine())
			throw new IOException(FILENAME + " is missing required 'engine' node");
	}
	public String toString() {
		return "Manifest(" + file.getPath() + ")";
	}

	public String getName() {
		return doc.nodes().stream()
			.filter(n -> n.name().equals("name"))
			.findFirst()
			.map(n -> n.arguments().get(0).value().toString())
			.orElse(file.getParentFile().getName());
	}

	public String getId() {
		return doc.nodes().stream()
			.filter(n -> n.name().equals("id"))
			.findFirst()
			.map(n -> n.arguments().get(0).value().toString())
			.orElse(null);
	}

	private boolean hasEngine() {
		return doc.nodes().stream()
			.anyMatch(n -> n.name().equals("engine"));
	}

	/**
	 * Returns the engine ref (git ref to checkout).
	 * Defaults to "main" if the ref property is omitted.
	 */
	public String getEngineRef() {
		return doc.nodes().stream()
			.filter(n -> n.name().equals("engine"))
			.findFirst()
			.map(n -> n.getProperty("ref")
				.map(v -> v.value().toString())
				.orElse("main"))
			.orElse("main");
	}
}
