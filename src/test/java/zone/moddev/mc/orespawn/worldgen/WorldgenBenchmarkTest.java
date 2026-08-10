package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void vanillaControlBypassesEveryForge112GenerationEntryPoint() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnWorldGenerator.java")),
				StandardCharsets.UTF_8);
		assertEquals(5, occurrences(source, "if (WorldgenBenchmark.isVanillaBaseline()) return;"),
				"terrain, springs, ores, ore filtering, and IWorldGenerator must all be bypassed");
	}

	private static int occurrences(String value, String needle) {
		int result = 0;
		for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) {
			result++;
		}
		return result;
	}
}
