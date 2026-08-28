package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String text = new String(Files.readAllBytes(
					Paths.get(".github", "workflows", workflow)), StandardCharsets.UTF_8);
			int jobCount = workflow.equals("ci.yml") ? 2 : 1;
			assertEquals(jobCount * 2, occurrences(text, "actions/setup-java@"),
					workflow + " must install one Mavenizer and one production JDK per job");
			assertTrue(text.contains("distribution: temurin"),
					workflow + " must use Temurin");
			assertEquals(jobCount, occurrences(text, "java-version: '25.0.3+9.0.LTS'"),
					workflow + " must install the exact Mavenizer Java runtime");
			assertEquals(jobCount, occurrences(text, "java-version: '17.0.1+12'"),
					workflow + " must install the exact qualified Java runtime");
			assertTrue(text.lastIndexOf("java-version: '17.0.1+12'")
					> text.lastIndexOf("java-version: '25.0.3+9.0.LTS'"),
					workflow + " must leave Java 17 as JAVA_HOME");
			assertFalse(text.contains("distribution: microsoft"),
					workflow + " must not replace the exact Temurin Gradle runtime");
		}
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
