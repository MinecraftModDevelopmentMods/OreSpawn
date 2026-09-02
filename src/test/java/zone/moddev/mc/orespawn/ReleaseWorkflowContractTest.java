package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ReleaseWorkflowContractTest {
	@Test
	void usesOreSpawnSpecificMavenNamespace() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
			properties.load(input);
		}
		assertEquals("zone.moddev.mc.orespawn", properties.getProperty("mod_group"));
	}

	@Test
	void verifiesGeneratedMavenCoordinatesBeforeCheckAndPublication() throws Exception {
		Path buildFile = Paths.get("build.gradle");
		String build = new String(Files.readAllBytes(buildFile), StandardCharsets.UTF_8);
		assertTrue(build.contains("tasks.register('verifyMavenCoordinates')"));
		assertTrue(build.contains("generatePomFileForMavenJavaPublication"));
		assertTrue(build.contains("dependsOn tasks.named('verifyMavenCoordinates')"));
		assertTrue(build.contains("expectedMavenCoordinate"));
	}

	@Test
	void hostedWorkflowsUseThePinnedTemurinJdkForGradleAndCompilation() throws Exception {
		String build = new String(Files.readAllBytes(Paths.get("build.gradle")),
				StandardCharsets.UTF_8);
		assertTrue(build.contains("fgtools.configure('slimelauncher')"),
				"ForgeGradle launcher metadata must use the qualified Java 25 toolchain");
		for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String text = new String(Files.readAllBytes(
					Paths.get(".github", "workflows", workflow)), StandardCharsets.UTF_8);
			assertEquals(1, occurrences(text, "actions/setup-java@"));
			assertEquals(1, occurrences(text, "java-version: '25.0.3+9'"));
			assertTrue(text.contains("distribution: temurin"));
		}
	}

	@Test
	void eclipseOutputsCannotNestInsideForgeMergedOutput() throws Exception {
		String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
		assertTrue(build.contains("defaultOutputDir = file('bin/default')"));
		assertTrue(build.contains("entry instanceof org.gradle.plugins.ide.eclipse.model.Output"));
		assertTrue(build.contains("entry.path = 'bin/default'"));
		assertTrue(build.contains("entry.output = 'bin/main'"));
		assertTrue(build.contains("entry.output = 'bin/test'"));
		assertTrue(build.contains("Eclipse outputs must be disjoint"),
				"The real generated classpath must reject nested Buildship outputs");
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		int offset = 0;
		while ((offset = text.indexOf(needle, offset)) >= 0) {
			count++;
			offset += needle.length();
		}
		return count;
	}
}
