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

	public Manifest(Project project) throws IOException, KdlParseException {
		file = new File(project.getPath(), FILENAME);

		if (!file.exists()) {
			throw new IOException(FILENAME + " does not exist");
		}

		var parser = KdlParser.v2();
		doc = parser.parse(Path.of(file.getAbsolutePath()));
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
}
