package zone.moddev.mc.orespawn.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
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
		Set<String> trackedFiles = relativeFiles(Paths.get("docs"), Paths.get("docs"));
		Set<String> exportedFiles = relativeFiles(temporaryDirectory, temporaryDirectory);
		assertEquals(trackedFiles.size(), firstExport);
		assertEquals(trackedFiles, exportedFiles);

		Path readme = temporaryDirectory.resolve("README.md");
		Files.write(readme, "local note".getBytes(StandardCharsets.UTF_8));
		assertEquals(0, DocumentationExporter.exportMissing(temporaryDirectory));
		assertEquals("local note", new String(Files.readAllBytes(readme), StandardCharsets.UTF_8));
	}

	private static Set<String> relativeFiles(Path root, Path current) throws Exception {
		try (Stream<Path> paths = Files.walk(current)) {
			return paths.filter(Files::isRegularFile)
					.map(root::relativize)
					.map(path -> path.toString().replace('\\', '/'))
					.collect(Collectors.toSet());
		}
	}
}
