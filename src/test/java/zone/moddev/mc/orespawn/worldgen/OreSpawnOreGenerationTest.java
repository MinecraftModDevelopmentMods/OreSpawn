package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

class OreSpawnOreGenerationTest {
	static {
		Bootstrap.bootStrap();
	}

	@Test
	void biomeFiltersRetainUnknownDynamicRegistryKeys() {
		RegistryKey<Biome> sodaOcean = RegistryKey.create(Registry.BIOME_REGISTRY,
				new ResourceLocation("cakeworld", "soda_ocean"));
		JsonObject rule = new JsonObject();
		JsonArray ids = new JsonArray();
		ids.add("cakeworld:soda_ocean");
		rule.add("biome_ids", ids);

		Set<?> resolved = OreSpawnOreGeneration.resolveBiomes(
				rule, "biome_ids", "biome_dictionary");

		assertEquals(Collections.singleton(sodaOcean), resolved);
	}

	@Test
	void biomeFiltersMergeDictionaryKeys() {
		RegistryKey<Biome> sodaOcean = RegistryKey.create(Registry.BIOME_REGISTRY,
				new ResourceLocation("cakeworld", "soda_ocean"));
		JsonObject rule = new JsonObject();
		JsonArray dictionary = new JsonArray();
		dictionary.add("OCEAN");
		rule.add("biome_dictionary", dictionary);

		Set<RegistryKey<Biome>> resolved = OreSpawnOreGeneration.resolveBiomes(
				rule, "biome_ids", "biome_dictionary", type -> Collections.singleton(sodaOcean));

		assertEquals(Collections.singleton(sodaOcean), resolved);
		assertTrue(OreSpawnOreGeneration.acceptsBiome(resolved, Collections.emptySet(), sodaOcean));
		assertFalse(OreSpawnOreGeneration.acceptsBiome(resolved, Collections.emptySet(), RegistryKey.create(
				Registry.BIOME_REGISTRY, new ResourceLocation("cakeworld", "candy_plains"))));
		assertFalse(OreSpawnOreGeneration.acceptsBiome(Collections.emptySet(), resolved, sodaOcean));
		assertTrue(OreSpawnOreGeneration.acceptsBiome(Collections.emptySet(), resolved, RegistryKey.create(
				Registry.BIOME_REGISTRY, new ResourceLocation("cakeworld", "candy_plains"))));
	}

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
		assertTrue(OreSpawnOreGeneration.selectorAllows(World.OVERWORLD));
		assertTrue(OreSpawnOreGeneration.selectorAllows(RegistryKey.create(
				Registry.DIMENSION_REGISTRY, new ResourceLocation("examplemod:moon"))));
		assertFalse(OreSpawnOreGeneration.selectorAllows(World.NETHER));
		assertFalse(OreSpawnOreGeneration.selectorAllows(World.END));
	}

	@Test
	void explicitDimensionRulesOverrideSelectorFallbacks() {
		Map<RegistryKey<World>, String> explicit = new HashMap<>();
		Set<RegistryKey<World>> configured = new HashSet<>();
		explicit.put(World.OVERWORLD, "overworld");
		configured.add(World.OVERWORLD);
		configured.add(RegistryKey.create(Registry.DIMENSION_REGISTRY,
				new ResourceLocation("examplemod:disabled")));

		assertEquals("overworld", OreSpawnOreGeneration.selectRule(
				explicit, configured, "selector", World.OVERWORLD));
		assertEquals("selector", OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("examplemod:moon"))));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				RegistryKey.create(Registry.DIMENSION_REGISTRY, new ResourceLocation("examplemod:disabled"))));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector", World.NETHER));
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
