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
	void declaresTheHostedSelectorForTheExactTemurinToolchain() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
			properties.load(input);
		}
		assertEquals("25.0.3+9", properties.getProperty("java_toolchain_version"));
		assertEquals("25.0.3+9.0.LTS", properties.getProperty("java_setup_version"));
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
			int jobCount = workflow.equals("ci.yml") ? 2 : 1;
			int gradleInvocationCount = workflow.equals("ci.yml") ? 3 : 1;
			assertEquals(jobCount, occurrences(text, "actions/setup-java@"),
					workflow + " must install the single Forge 65 Java 25 toolchain per job");
			assertTrue(text.contains("distribution: temurin"), workflow + " must use Temurin");
			assertEquals(jobCount, occurrences(text, "java-version: '25.0.3+9.0.LTS'"),
					workflow + " must install the exact Java 25 runtime and toolchain");
			assertEquals(gradleInvocationCount, occurrences(text,
					"-Dorg.gradle.java.installations.paths="),
					workflow + " must limit Gradle discovery to the pinned JDKs");
			assertEquals(gradleInvocationCount, occurrences(text,
					"$JAVA_HOME"), workflow + " must use only the explicit Java 25 path");
			assertFalse(text.contains("JAVA_HOME_8_X64") || text.contains("JAVA_HOME_21_X64"),
					workflow + " must not inherit older-target launcher or production JDKs");
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
