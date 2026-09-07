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
	void hostedWorkflowsUsePinnedJdksAndExerciseAColdForgeBootstrap() throws Exception {
		String ci = readWorkflow("ci.yml");
		String codeql = readWorkflow("codeql-analysis.yml");

		for (String workflow : new String[] { ci, codeql }) {
			assertFalse(workflow.contains("distribution: microsoft"));
			assertTrue(workflow.contains("java-version: '8.0.502+7'"));
			assertTrue(workflow.contains("java-version: '17.0.1+12'"));
			assertTrue(workflow.lastIndexOf("java-version: '17.0.1+12'")
					> workflow.lastIndexOf("java-version: '8.0.502+7'"));
			assertTrue(workflow.contains("$JAVA_HOME,$JAVA_HOME_8_X64"));
			assertTrue(workflow.contains("-Dorg.gradle.java.installations.auto-detect=false"));
			assertTrue(workflow.contains("-Dorg.gradle.java.installations.auto-download=false"));
		}

		assertTrue(ci.contains("name: Cold Forge bootstrap"));
		assertTrue(ci.contains("GRADLE_USER_HOME: ${{ runner.temp }}/orespawn-cold-gradle"));
		assertTrue(ci.contains("test ! -e .gradle"));
		assertTrue(ci.contains("test ! -e \"$GRADLE_USER_HOME\""));
		assertTrue(ci.contains("classes verifyLegacyFixtures"));
		assertTrue(ci.contains("--rerun-tasks --offline --no-daemon --no-build-cache"));
		assertFalse(ci.contains("Mavenizer compatibility"));
		assertFalse(ci.contains("25.0.3"));
	}

	private static String readWorkflow(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(".github", "workflows", name)),
				StandardCharsets.UTF_8);
	}
}
