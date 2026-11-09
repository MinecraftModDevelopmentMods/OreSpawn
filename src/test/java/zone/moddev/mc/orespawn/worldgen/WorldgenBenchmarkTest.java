package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

class WorldgenBenchmarkTest {
	@Test
	void resolvesVanillaAliasesThroughTheStaticDimensionRegistry() {
		assertEquals(0, WorldgenBenchmark.benchmarkDimension("overworld"));
		assertEquals(-1, WorldgenBenchmark.benchmarkDimension("NETHER"));
		assertEquals(1, WorldgenBenchmark.benchmarkDimension(" end "));
	}

	@Test
	void rejectsInvalidCustomDimensionIds() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldgenBenchmark.benchmarkDimension("not a dimension"));
	}

	@Test
	void vanillaControlBypassesTheExistingGenerationEntryPoints() throws Exception {
		String source = coordinatorSource();
		for (String method : new String[] { "beforeBiomeDecoration", "replaceVanillaSprings",
				"beforeVanillaOres", "filterVanillaOre", "generate" }) {
			assertBaselineGuard(source, method);
		}
	}

	@Test
	void vanillaControlBypassesMesaRestorationBeforeReadingTheEvent() throws Exception {
		assertBaselineGuard(coordinatorSource(), "afterBiomeDecoration");
	}

	@Test
	void integrationBenchmarkCanProveNormalizedBlockAndBiomeParity() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "WorldgenBenchmark.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("orespawn.worldgenBenchmarkNormalizeBlock"));
		assertTrue(source.contains("orespawn.worldgenBenchmarkNormalizeBiome"));
		assertTrue(source.contains("ORESPAWN_BENCHMARK_HASH"));
		assertTrue(source.contains("orespawn.worldgenBenchmarkBiome"));
		assertTrue(source.indexOf("ORESPAWN_BENCHMARK summary")
				< source.indexOf("auditNormalizedHashes(level"),
				"hashing must happen outside the measured generation interval");
	}

	private static String coordinatorSource() throws Exception {
		return new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnWorldGenerator.java")),
				StandardCharsets.UTF_8);
	}

	private static void assertBaselineGuard(String source, String method) {
		int declaration = source.indexOf("public void " + method + "(");
		assertTrue(declaration >= 0, "Missing generation entry point: " + method);
		int body = source.indexOf('{', declaration);
		assertTrue(body >= 0, "Missing method body: " + method);
		assertTrue(source.substring(body + 1).trim()
				.startsWith("if (WorldgenBenchmark.isVanillaBaseline()) return;"),
				method + " must bypass generation work before reading its arguments");
	}
}
