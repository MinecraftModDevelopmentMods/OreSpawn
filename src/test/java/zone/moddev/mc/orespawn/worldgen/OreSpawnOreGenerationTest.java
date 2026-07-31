package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

class OreSpawnOreGenerationTest {
	@Test
	void fixedQuantityDoesNotConsumeRandomState() {
		CountingRandom random = new CountingRandom(0);
		assertEquals(8, OreSpawnOreGeneration.sampleQuantity(random, 8, 8));
		assertEquals(0, random.calls);
	}

	@Test
	void rangedQuantityReachesBothInclusiveBounds() {
		CountingRandom minimum = new CountingRandom(0);
		CountingRandom maximum = new CountingRandom(7);
		assertEquals(4, OreSpawnOreGeneration.sampleQuantity(minimum, 4, 11));
		assertEquals(11, OreSpawnOreGeneration.sampleQuantity(maximum, 4, 11));
		assertEquals(1, minimum.calls);
		assertEquals(1, maximum.calls);
	}

	@Test
	void broadSelectorNeverLeaksIntoNetherOrEnd() {
		assertTrue(OreSpawnOreGeneration.selectorAllows(Level.OVERWORLD));
		assertTrue(OreSpawnOreGeneration.selectorAllows(ResourceKey.create(
				Registries.DIMENSION, Identifier.parse("examplemod:moon"))));
		assertFalse(OreSpawnOreGeneration.selectorAllows(Level.NETHER));
		assertFalse(OreSpawnOreGeneration.selectorAllows(Level.END));
	}

	@Test
	void explicitDimensionRulesOverrideSelectorFallbacks() {
		Map<ResourceKey<Level>, String> explicit = new HashMap<>();
		Set<ResourceKey<Level>> configured = new HashSet<>();
		explicit.put(Level.OVERWORLD, "overworld");
		configured.add(Level.OVERWORLD);
		configured.add(ResourceKey.create(Registries.DIMENSION,
				Identifier.parse("examplemod:disabled")));

		assertEquals("overworld", OreSpawnOreGeneration.selectRule(
				explicit, configured, "selector", Level.OVERWORLD));
		assertEquals("selector", OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				ResourceKey.create(Registries.DIMENSION, Identifier.parse("examplemod:moon"))));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				ResourceKey.create(Registries.DIMENSION, Identifier.parse("examplemod:disabled"))));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector", Level.NETHER));
	}

	private static final class CountingRandom extends Random {
		private static final long serialVersionUID = 1L;
		private final int result;
		int calls;

		CountingRandom(int result) {
			this.result = result;
		}

		@Override
		public int nextInt(int bound) {
			calls++;
			return Math.min(result, bound - 1);
		}
	}
}
