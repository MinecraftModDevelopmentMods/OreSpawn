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
		assertEquals("zone.moddev.mc.orespawn", properties.getProperty("mod_group_id"));
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
		String publishing = new String(Files.readAllBytes(Paths.get("gradle", "release", "publishing.gradle")),
				StandardCharsets.UTF_8);
		String contracts = new String(Files.readAllBytes(Paths.get("gradle", "verification", "contracts.gradle")),
				StandardCharsets.UTF_8);
		assertTrue(build.contains("apply from: 'gradle/release/publishing.gradle'"));
		assertTrue(publishing.contains("tasks.register('verifyMavenCoordinates')"));
		assertTrue(publishing.contains("generatePomFileForMavenJavaPublication"));
		assertTrue(contracts.contains("dependsOn tasks.named('verifyMavenCoordinates')"));
		assertTrue(build.contains("expectedMavenCoordinate"));
	}

	@Test
	void hostedWorkflowsUseOnlyThePinnedTemurinJdk() throws Exception {
		for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String text = new String(Files.readAllBytes(
					Paths.get(".github", "workflows", workflow)), StandardCharsets.UTF_8);
			int jobCount = workflow.equals("ci.yml") ? 2 : 1;
			assertEquals(jobCount, occurrences(text, "actions/setup-java@"));
			assertEquals(jobCount, occurrences(text, "distribution: temurin"));
			assertEquals(jobCount, occurrences(text, "java-version: '25.0.3+9.0.LTS'"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.paths=$JAVA_HOME"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-detect=false"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-download=false"));
			if (workflow.equals("ci.yml")) {
				assertEquals(6, occurrences(text, "\"${gradle_jdk_args[@]}\""));
			} else {
				assertEquals(2, occurrences(text, "\"${gradle_jdk_args[@]}\""));
			}
			assertFalse(text.contains("21.0.7"));
			assertFalse(text.contains("8.0.502"));
			assertFalse(text.contains("MinecraftMavenizer"));
		}
	}

	@Test
	void codeQlUsesABoundedCachePreservingCompileRetry() throws Exception {
		String text = new String(Files.readAllBytes(
				Paths.get(".github", "workflows", "codeql-analysis.yml")), StandardCharsets.UTF_8);
		assertTrue(text.contains("./gradlew clean --no-daemon"));
		assertTrue(text.contains("gradle_args=("));
		assertTrue(text.contains("for attempt in 1 2 3; do"));
		assertTrue(text.contains("./gradlew \"${gradle_args[@]}\""));
		assertTrue(text.contains("failed after $attempt attempts"));
		assertFalse(text.contains("clean classes"));
	}

	@Test
	void neoGradleCleanRunsSeparatelyFromModelConsumers() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
			properties.load(input);
		}
		assertFalse(properties.containsKey("neogradle.subsystems.decompiler.enabled"));

		String build = new String(Files.readAllBytes(
				Paths.get("build.gradle")), StandardCharsets.UTF_8);
		assertTrue(build.contains("task.name.startsWith('cacheVersionExecutable')"));
		assertTrue(build.contains("!gradle.startParameter.offline || cachedOutputs.isEmpty()"));

		String text = new String(Files.readAllBytes(
				Paths.get(".github", "workflows", "ci.yml")), StandardCharsets.UTF_8);
		assertEquals(3, occurrences(text, "./gradlew clean "));
		assertTrue(text.contains("./gradlew classes verifyLegacyFixtures --no-daemon"));
		assertTrue(text.contains("./gradlew classes verifyLegacyFixtures --offline --no-daemon"));
		assertTrue(text.contains("./gradlew check build javadoc"));
		assertFalse(text.contains("./gradlew clean check"));
		assertFalse(text.contains("./gradlew clean classes"));
	}

	@Test
	void eclipseAlwaysPinsBuildshipToTheActiveJava25Runtime() throws Exception {
		String build = new String(Files.readAllBytes(
				Paths.get("gradle", "ide", "eclipse.gradle")), StandardCharsets.UTF_8);
		assertTrue(build.contains("existingGradleHome = preferences.getProperty("));
		assertTrue(build.contains("gradle.gradleUserHomeDir"));
		assertTrue(build.contains("preferences.setProperty('java.home', System.getProperty('java.home'))"));
		assertTrue(build.contains("JAVA_VERSION=\"25.0.3\""));
		assertTrue(build.contains("IMPLEMENTOR=\"Eclipse Adoptium\""));
		assertFalse(build.contains("requestedGradleHome == null || requestedGradleHome.trim().isEmpty()) {\n            return"));
	}

	@Test
	void eclipseVerifiesTheActualNeoGradleLaunchesAndExcludesTestCode() throws Exception {
		String script = Files.readString(Paths.get("gradle/ide/eclipse.gradle"));
		assertTrue(script.contains("fileTree('.eclipse/configurations')"));
		assertTrue(script.contains("org.eclipse.jdt.launching.ATTR_EXCLUDE_TEST_CODE"));
		assertTrue(script.contains("The ordinary client must keep its own run directory"));
		assertTrue(script.contains("runTestOreSourcesClient.launch"));
	}

	@Test
	void releaseChecksumsTrackTheActualJarInputs() throws Exception {
		String script = Files.readString(Paths.get("gradle/release/artifacts.gradle"));
		String task = script.substring(script.indexOf("tasks.register('writeReleaseChecksums')"));
		assertTrue(task.contains("inputs.files(expectedReleaseFiles.map"));
		assertTrue(task.contains("libs/${it}"));
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
