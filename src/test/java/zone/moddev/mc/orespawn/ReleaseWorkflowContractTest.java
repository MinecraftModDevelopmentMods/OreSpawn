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

		for (String workflowName : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String workflow = readWorkflow(workflowName);
			int jobCount = workflowName.equals("ci.yml") ? 2 : 1;
			int gradleInvocationCount = workflowName.equals("ci.yml") ? 3 : 1;
			assertEquals(jobCount * 4, occurrences(workflow, "actions/setup-java@"),
					workflowName + " must install Mavenizer, launcher, compilation, and Gradle JDKs per job");
			assertFalse(workflow.contains("distribution: microsoft"));
			assertEquals(jobCount, occurrences(workflow, "java-version: '25.0.3+9.0.LTS'"),
					workflowName + " must install the exact ForgeGradle Mavenizer runtime");
			assertEquals(jobCount, occurrences(workflow, "java-version: '8.0.502+7'"),
					workflowName + " must install the exact legacy launcher toolchain");
			assertEquals(jobCount, occurrences(workflow, "java-version: '16.0.2+7'"),
					workflowName + " must install the exact compilation toolchain");
			assertEquals(jobCount, occurrences(workflow, "java-version: '17.0.1+12'"),
					workflowName + " must install the exact Gradle runtime");
			assertTrue(workflow.lastIndexOf("java-version: '17.0.1+12'")
					> workflow.lastIndexOf("java-version: '16.0.2+7'"));
			assertTrue(workflow.lastIndexOf("java-version: '17.0.1+12'")
					> workflow.lastIndexOf("java-version: '25.0.3+9.0.LTS'"),
					workflowName + " must install Java 17 last so it remains JAVA_HOME");
			assertTrue(workflow.lastIndexOf("java-version: '17.0.1+12'")
					> workflow.lastIndexOf("java-version: '8.0.502+7'"));
			assertEquals(gradleInvocationCount, occurrences(workflow,
					"$JAVA_HOME,$JAVA_HOME_8_X64,$JAVA_HOME_16_X64,$JAVA_HOME_25_X64"),
					workflowName + " must expose only the four pinned JDKs to Gradle");
			assertEquals(gradleInvocationCount, occurrences(workflow,
					"-Dorg.gradle.java.installations.auto-detect=false"));
			assertEquals(gradleInvocationCount, occurrences(workflow,
					"-Dorg.gradle.java.installations.auto-download=false"));
		}

		assertTrue(ci.contains("name: Cold Forge bootstrap"));
		assertTrue(ci.contains("GRADLE_USER_HOME: ${{ runner.temp }}/orespawn-cold-gradle"));
		assertTrue(ci.contains("test ! -e .gradle"));
		assertTrue(ci.contains("test ! -e \"$GRADLE_USER_HOME\""));
		assertTrue(ci.contains("classes verifyLegacyFixtures"));
		assertTrue(ci.contains("--rerun-tasks --offline --no-daemon --no-build-cache"));
		assertFalse(ci.contains("verifyMavenizerCompatibilityFixture"),
				"Forge 37 must continue using ForgeGradle's stock Mavenizer");
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

	private static String readWorkflow(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(".github", "workflows", name)),
				StandardCharsets.UTF_8);
	}
}
