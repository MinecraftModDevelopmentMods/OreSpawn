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
		assertEquals("21.0.7+6", properties.getProperty("java_toolchain_version"));
		assertEquals("21.0.7+6.0.LTS", properties.getProperty("java_setup_version"));
	}

	@Test
	void verifiesGeneratedMavenCoordinatesBeforeCheckAndPublication() throws Exception {
		Path publishingFile = Paths.get("gradle", "release", "publishing.gradle");
		String publishing = new String(Files.readAllBytes(publishingFile), StandardCharsets.UTF_8);
		assertTrue(publishing.contains("tasks.register('verifyMavenCoordinates')"));
		assertTrue(publishing.contains("generatePomFileForMavenJavaPublication"));
		assertTrue(publishing.contains("dependsOn tasks.named('verifyMavenCoordinates')"));
		String root = new String(Files.readAllBytes(Paths.get("build.gradle")),
				StandardCharsets.UTF_8);
		assertTrue(root.contains("expectedMavenCoordinate"));
	}

	@Test
	void hostedWorkflowsUseOnlyThePinnedTemurinJdk() throws Exception {
		for (String workflow : new String[] { "ci.yml", "codeql-analysis.yml" }) {
			String text = new String(Files.readAllBytes(
					Paths.get(".github", "workflows", workflow)), StandardCharsets.UTF_8);
			int jobCount = workflow.equals("ci.yml") ? 2 : 1;
			assertEquals(jobCount, occurrences(text, "actions/setup-java@"));
			assertEquals(jobCount, occurrences(text, "distribution: temurin"));
			assertEquals(jobCount, occurrences(text, "java-version: '21.0.7+6.0.LTS'"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.paths=$JAVA_HOME"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-detect=false"));
			assertEquals(jobCount, occurrences(text,
					"-Dorg.gradle.java.installations.auto-download=false"));
			if (workflow.equals("ci.yml")) {
				assertEquals(4, occurrences(text, "\"${gradle_jdk_args[@]}\""),
						"Cold online/offline and separate clean/build calls use the same JDK");
			}
			assertFalse(text.contains("25.0.3"));
			assertFalse(text.contains("8.0.502"));
			assertFalse(text.contains("MinecraftMavenizer"));
		}
	}

	@Test
	void ciCompilesColdClassesAndCleansInASeparateInvocation() throws Exception {
		String text = new String(Files.readAllBytes(
				Paths.get(".github", "workflows", "ci.yml")), StandardCharsets.UTF_8);
		assertEquals(2, occurrences(text, "./gradlew classes verifyLegacyFixtures"));
		assertTrue(text.contains("./gradlew classes verifyLegacyFixtures --offline"));
		assertFalse(text.contains("./gradlew help verifyLegacyFixtures"));
		assertTrue(text.contains("test ! -e \"$GRADLE_USER_HOME\""));
		assertFalse(text.contains("rm -rf"));
		assertTrue(text.contains("./gradlew clean --no-daemon"));
		assertTrue(text.contains("./gradlew check build javadoc"));
		assertFalse(text.contains("./gradlew clean check"));
	}

	@Test
	void codeQlUsesABoundedCachePreservingCompileRetry() throws Exception {
		String text = new String(Files.readAllBytes(
				Paths.get(".github", "workflows", "codeql-analysis.yml")), StandardCharsets.UTF_8);
		assertTrue(text.contains("gradle_args=("));
		assertTrue(text.contains("for attempt in 1 2 3; do"));
		assertTrue(text.contains("./gradlew \"${gradle_args[@]}\""));
		assertTrue(text.contains("failed after $attempt attempts"));
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
