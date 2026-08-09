package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.world.dimension.DimensionType;

class WorldgenBenchmarkTest {
	@Test
	void resolvesVanillaAliasesThroughTheStaticDimensionRegistry() {
		assertEquals(DimensionType.OVERWORLD, WorldgenBenchmark.benchmarkDimension("overworld"));
		assertEquals(DimensionType.THE_NETHER, WorldgenBenchmark.benchmarkDimension("NETHER"));
		assertEquals(DimensionType.THE_END, WorldgenBenchmark.benchmarkDimension(" end "));
	}

	@Test
	void rejectsInvalidCustomDimensionIds() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldgenBenchmark.benchmarkDimension("not a dimension"));
	}
}
