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

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

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
		assertTrue(OreSpawnOreGeneration.selectorAllows(WorldIds.OVERWORLD));
		assertTrue(OreSpawnOreGeneration.selectorAllows(new ResourceLocation("examplemod:moon")));
		assertFalse(OreSpawnOreGeneration.selectorAllows(WorldIds.NETHER));
		assertFalse(OreSpawnOreGeneration.selectorAllows(WorldIds.END));
	}

	@Test
	void explicitDimensionRulesOverrideSelectorFallbacks() {
		Map<ResourceLocation, String> explicit = new HashMap<>();
		Set<ResourceLocation> configured = new HashSet<>();
		explicit.put(WorldIds.OVERWORLD, "overworld");
		configured.add(WorldIds.OVERWORLD);
		configured.add(new ResourceLocation("examplemod:disabled"));

		assertEquals("overworld", OreSpawnOreGeneration.selectRule(
				explicit, configured, "selector", WorldIds.OVERWORLD));
		assertEquals("selector", OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				new ResourceLocation("examplemod:moon")));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				new ResourceLocation("examplemod:disabled")));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector", WorldIds.NETHER));
	}

	@Test
	void metadataOnlyReplacementHostsAreValidOreTargets() {
		Map<Block, Double> blocks = new HashMap<>();
		Map<IBlockState, Double> states = new HashMap<>();
		// The parser stores a metadata-qualified host in the exact-state map,
		// independently of the whole-block map.
		states.put(null, 1.0D);

		assertTrue(OreSpawnOreGeneration.hasHostTargets(blocks, states, 0));
		states.clear();
		assertFalse(OreSpawnOreGeneration.hasHostTargets(blocks, states, 0));
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
