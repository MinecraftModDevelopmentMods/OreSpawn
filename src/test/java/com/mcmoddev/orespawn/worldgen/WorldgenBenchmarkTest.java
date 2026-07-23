package com.mcmoddev.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

class WorldgenBenchmarkTest {
	@Test
	void resolvesVanillaAliasesAndCustomDimensionIds() {
		assertEquals(Level.OVERWORLD, WorldgenBenchmark.benchmarkDimensionKey("overworld"));
		assertEquals(Level.NETHER, WorldgenBenchmark.benchmarkDimensionKey("NETHER"));
		assertEquals(Level.END, WorldgenBenchmark.benchmarkDimensionKey(" end "));
		assertEquals(ResourceKey.create(Registry.DIMENSION_REGISTRY,
				new ResourceLocation("test", "ordinary")),
				WorldgenBenchmark.benchmarkDimensionKey("test:ordinary"));
	}

	@Test
	void rejectsInvalidCustomDimensionIds() {
		assertThrows(IllegalArgumentException.class,
				() -> WorldgenBenchmark.benchmarkDimensionKey("not a dimension"));
	}
}
