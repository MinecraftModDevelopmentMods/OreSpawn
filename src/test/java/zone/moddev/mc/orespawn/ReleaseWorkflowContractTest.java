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
			int gradleInvocationCount = workflow.equals("ci.yml") ? 3 : 1;
			assertEquals(jobCount * 3, occurrences(text, "actions/setup-java@"),
					workflow + " must install Mavenizer, launcher, and production JDKs per job");
			assertTrue(text.contains("distribution: temurin"), workflow + " must use Temurin");
			assertEquals(jobCount, occurrences(text, "java-version: '25.0.3+9.0.LTS'"),
					workflow + " must install the exact Mavenizer Java runtime");
			assertEquals(jobCount, occurrences(text, "java-version: '8.0.502+7'"),
					workflow + " must install the exact legacy launcher toolchain");
			assertEquals(jobCount, occurrences(text, "java-version: '21.0.7+6'"),
					workflow + " must install the exact qualified Java runtime");
			assertTrue(text.lastIndexOf("java-version: '21.0.7+6'")
					> text.lastIndexOf("java-version: '25.0.3+9.0.LTS'"),
					workflow + " must leave Java 21 as JAVA_HOME");
			assertTrue(text.lastIndexOf("java-version: '21.0.7+6'")
					> text.lastIndexOf("java-version: '8.0.502+7'"),
					workflow + " must install Java 21 last so it remains JAVA_HOME");
			assertEquals(gradleInvocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.paths="),
					workflow + " must limit Gradle discovery to the pinned JDKs");
			assertEquals(gradleInvocationCount, occurrences(text,
					"$JAVA_HOME,$JAVA_HOME_8_X64,$JAVA_HOME_25_X64"),
					workflow + " must use only the explicit pinned JDK paths");
			assertEquals(gradleInvocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-detect=false"),
					workflow + " must reject preinstalled runner toolchains");
			assertEquals(gradleInvocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-download=false"),
					workflow + " must not silently replace pinned toolchains");
			assertFalse(text.contains("distribution: microsoft"),
					workflow + " must not replace the exact Temurin Gradle runtime");
		}
	}

	@Test
	void codeQlUsesABoundedCachePreservingCompileRetry() throws Exception {
		String text = new String(Files.readAllBytes(
				Paths.get(".github", "workflows", "codeql-analysis.yml")), StandardCharsets.UTF_8);
		assertTrue(text.contains("gradle_args=("), "CodeQL must pass Gradle options as an argument vector");
		assertTrue(text.contains("for attempt in 1 2 3; do"),
				"CodeQL must bound transient Mavenizer download retries");
		assertTrue(text.contains("./gradlew \"${gradle_args[@]}\""),
				"CodeQL retries must preserve exact Gradle arguments");
		assertTrue(text.contains("failed after $attempt attempts"),
				"CodeQL must fail rather than hide a persistent bootstrap defect");
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
