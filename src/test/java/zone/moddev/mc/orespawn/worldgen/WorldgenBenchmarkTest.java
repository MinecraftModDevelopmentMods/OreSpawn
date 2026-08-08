package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

class WorldgenBenchmarkTest {
	@Test
	void resolvesVanillaAliasesAndCustomDimensionIds() {
		assertEquals(World.OVERWORLD, WorldgenBenchmark.benchmarkDimensionKey("overworld"));
		assertEquals(World.NETHER, WorldgenBenchmark.benchmarkDimensionKey("NETHER"));
		assertEquals(World.END, WorldgenBenchmark.benchmarkDimensionKey(" end "));
		assertEquals(RegistryKey.create(Registry.DIMENSION_REGISTRY,
				new ResourceLocation("test", "ordinary")),
				WorldgenBenchmark.benchmarkDimensionKey("test:ordinary"));
	}

	@Test
	void rejectsInvalidCustomDimensionIds() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldgenBenchmark.benchmarkDimensionKey("not a dimension"));
	}
}
