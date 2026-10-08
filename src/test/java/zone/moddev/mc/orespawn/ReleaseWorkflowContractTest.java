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
		StringBuilder sources = new StringBuilder();
		for (Path buildFile : new Path[] { Paths.get("build.gradle"),
				Paths.get("gradle", "release", "artifacts.gradle"),
				Paths.get("gradle", "release", "publishing.gradle") }) {
			sources.append(new String(Files.readAllBytes(buildFile), StandardCharsets.UTF_8));
		}
		String build = sources.toString();
		assertTrue(build.contains("tasks.register('verifyMavenCoordinates')"));
		assertTrue(build.contains("generatePomFileForMavenJavaPublication"));
		assertTrue(build.contains("dependsOn tasks.named('verifyMavenCoordinates')"));
		assertTrue(build.contains("expectedMavenCoordinate"));
	}

	@Test
	void hostedWorkflowsUsePinnedJdksAndExerciseAColdForgeBootstrap() throws Exception {
		for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String text = new String(Files.readAllBytes(
					Paths.get(".github", "workflows", workflow)), StandardCharsets.UTF_8);
			int jobCount = workflow.equals("ci.yml") ? 2 : 1;
			int invocationCount = workflow.equals("ci.yml") ? 3 : 1;
			assertEquals(jobCount * 3, occurrences(text, "actions/setup-java@"));
			assertEquals(jobCount, occurrences(text, "java-version: '25.0.3+9.0.LTS'"));
			assertEquals(jobCount, occurrences(text, "java-version: '8.0.502+7'"));
			assertEquals(jobCount, occurrences(text, "java-version: '17.0.1+12'"));
			assertTrue(text.lastIndexOf("java-version: '17.0.1+12'")
					> text.lastIndexOf("java-version: '25.0.3+9.0.LTS'"));
			assertTrue(text.lastIndexOf("java-version: '17.0.1+12'")
					> text.lastIndexOf("java-version: '8.0.502+7'"));
			assertEquals(invocationCount, occurrences(text,
					"$JAVA_HOME,$JAVA_HOME_8_X64,$JAVA_HOME_25_X64"));
			assertEquals(invocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-detect=false"));
			assertEquals(invocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-download=false"));
			assertTrue(text.contains("distribution: temurin"),
					workflow + " must use Temurin");
			assertTrue(text.contains("java-version: '17.0.1+12'"),
					workflow + " must install the exact qualified Java runtime");
			assertFalse(text.contains("distribution: microsoft"),
					workflow + " must not replace the exact Temurin Gradle runtime");
		}
		String ci = Files.readString(Paths.get(".github", "workflows", "ci.yml"));
		assertTrue(ci.contains("name: Cold Forge bootstrap"));
		assertTrue(ci.contains("test ! -e .gradle"));
		assertTrue(ci.contains("test ! -e \"$GRADLE_USER_HOME\""));
		assertTrue(ci.contains("classes verifyLegacyFixtures verifyMavenizerCompatibilityFixture"));
		assertTrue(ci.contains("--rerun-tasks --offline --no-daemon --no-build-cache"));
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
