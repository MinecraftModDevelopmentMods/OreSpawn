package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ReleaseWorkflowContractTest {
	@Test
	void releaseAndHostedWorkflowContractsRemainTargetNative() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
			properties.load(input);
		}
		assertEquals("zone.moddev.mc.orespawn", properties.getProperty("mod_group"));

		String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);
		assertTrue(build.contains("tasks.register('verifyMavenCoordinates')"));
		assertTrue(build.contains("generatePomFileForMavenJavaPublication"));
		assertTrue(build.contains("dependsOn tasks.named('verifyMavenCoordinates')"));
		assertTrue(build.contains("expectedMavenCoordinate"));
		assertFalse(build.contains("Mavenizer compatibility"));

		String ci = readWorkflow("ci.yml");
		String codeql = readWorkflow("codeql-analysis.yml");
		for (String workflow : new String[] { ci, codeql }) {
			assertFalse(workflow.contains("distribution: microsoft"));
			assertTrue(workflow.contains("java-version: '25.0.3+9.0.LTS'"));
			assertTrue(workflow.contains("java-version: '8.0.502+7'"));
			assertTrue(workflow.contains("java-version: '17.0.1+12'"));
			assertTrue(workflow.contains("-Dorg.gradle.java.installations.auto-detect=false"));
			assertTrue(workflow.contains("-Dorg.gradle.java.installations.auto-download=false"));
		}
		assertPinnedToolchains(ci, 2, 3);
		assertPinnedToolchains(codeql, 1, 1);
		assertTrue(ci.contains("name: Cold Forge bootstrap"));
		assertTrue(ci.contains("GRADLE_USER_HOME: ${{ runner.temp }}/orespawn-cold-gradle"));
		assertTrue(ci.contains("test ! -e .gradle"));
		assertTrue(ci.contains("test ! -e \"$GRADLE_USER_HOME\""));
		assertTrue(ci.contains("classes verifyLegacyFixtures"));
		assertTrue(ci.contains("--rerun-tasks --offline --no-daemon --no-build-cache"));
		assertTrue(build.contains("if (gradle.startParameter.offline)"));
		assertTrue(build.contains("mavenizerArguments.add('--offline')"));
		assertTrue(build.contains("Minecraft 1.11's LegacyV2Adapter"));
		assertTrue(build.contains("Packaged LegacyV2 locale alias differs"));
		assertTrue(build.contains("args 'nogui'"));
		assertFalse(build.contains("args '--nogui'"));
		assertTrue(build.contains("List<String> effectiveArgs = new ArrayList<>(originalArgs)"));
		assertTrue(build.contains("run.setArgs(effectiveArgs)"));
		assertFalse(build.contains("run.args(process.args)"));
		assertTrue(build.contains("'-jar', forge, 'nogui'"));
		assertTrue(build.contains("logs/fml-server-latest.log"));
		assertTrue(build.contains("benchmarkLogs.any"));
	}

	private static String readWorkflow(String name) throws Exception {
		return new String(Files.readAllBytes(Paths.get(".github", "workflows", name)),
				StandardCharsets.UTF_8);
	}

	private static void assertPinnedToolchains(String workflow, int expectedJobs, int expectedPathUses) {
		String java25 = "java-version: '25.0.3+9.0.LTS'";
		String java8 = "java-version: '8.0.502+7'";
		String java17 = "java-version: '17.0.1+12'";
		String paths = "$JAVA_HOME,$JAVA_HOME_8_X64,$JAVA_HOME_25_X64";
		assertEquals(expectedJobs, occurrences(workflow, java25));
		assertEquals(expectedJobs, occurrences(workflow, java8));
		assertEquals(expectedJobs, occurrences(workflow, java17));
		assertEquals(expectedPathUses, occurrences(workflow, paths));
		int cursor = 0;
		for (int job = 0; job < expectedJobs; job++) {
			int java25Index = workflow.indexOf(java25, cursor);
			int java8Index = workflow.indexOf(java8, java25Index + java25.length());
			int java17Index = workflow.indexOf(java17, java8Index + java8.length());
			assertTrue(java25Index >= cursor);
			assertTrue(java8Index > java25Index);
			assertTrue(java17Index > java8Index);
			cursor = java17Index + java17.length();
		}
	}

	private static int occurrences(String value, String needle) {
		int count = 0;
		int offset = 0;
		while ((offset = value.indexOf(needle, offset)) >= 0) {
			count++;
			offset += needle.length();
		}
		return count;
	}
}
