package com.mcmoddev.orespawn.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentationExporterTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void exportsCompleteGuideAndDoesNotOverwriteExistingFiles() throws Exception {
		int firstExport = DocumentationExporter.exportMissing(temporaryDirectory);
		assertTrue(firstExport >= 19);
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("README.md")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("DEVELOPER_GUIDE.md")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("examples/examplemod-orespawn.json")));
		assertTrue(Files.isRegularFile(temporaryDirectory.resolve("schemas/orespawn-provider.schema.json")));

		Path readme = temporaryDirectory.resolve("README.md");
		Files.write(readme, "local note".getBytes(StandardCharsets.UTF_8));
		assertEquals(0, DocumentationExporter.exportMissing(temporaryDirectory));
		assertEquals("local note", new String(Files.readAllBytes(readme), StandardCharsets.UTF_8));
	}
}
