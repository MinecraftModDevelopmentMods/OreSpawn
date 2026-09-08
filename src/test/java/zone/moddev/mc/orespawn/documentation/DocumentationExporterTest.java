package zone.moddev.mc.orespawn.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationExporterTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void exportsCompleteGuideAndDoesNotOverwriteExistingFiles() throws Exception {
		int firstExport = DocumentationExporter.exportMissing(temporaryDirectory);
		List<Path> tracked;
		try (Stream<Path> paths = Files.walk(Paths.get("docs"))) {
			tracked = paths.filter(Files::isRegularFile)
					.sorted()
					.collect(Collectors.toList());
		}
		assertEquals(21, tracked.size());
		assertEquals(tracked.size(), firstExport);
		for (Path source : tracked) {
			Path relative = Paths.get("docs").relativize(source);
			Path exported = temporaryDirectory.resolve(relative.toString());
			assertTrue(Files.isRegularFile(exported), "missing runtime export " + relative);
			assertEquals(new String(Files.readAllBytes(source), StandardCharsets.UTF_8),
					new String(Files.readAllBytes(exported), StandardCharsets.UTF_8),
					"runtime export differs for " + relative);
		}
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("README.md")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("DEVELOPER_GUIDE.md")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("BIOMES.md")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("examples/examplemod-orespawn.json")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("schemas/orespawn-provider.schema.json")));

		Path readme = temporaryDirectory.resolve("README.md");
		Files.write(readme, "local note".getBytes(StandardCharsets.UTF_8));
		assertEquals(0, DocumentationExporter.exportMissing(temporaryDirectory));
		assertEquals("local note", new String(Files.readAllBytes(readme), StandardCharsets.UTF_8));
	}
}
