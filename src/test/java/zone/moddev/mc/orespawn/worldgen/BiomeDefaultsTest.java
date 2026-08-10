package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class BiomeDefaultsTest {
	private static final String MINECRAFT_113_OVERWORLD =
			"ocean plains desert mountains forest taiga swamp river frozen_ocean frozen_river "
			+ "snowy_tundra snowy_mountains mushroom_fields mushroom_field_shore beach desert_hills "
			+ "wooded_hills taiga_hills mountain_edge jungle jungle_hills jungle_edge deep_ocean "
			+ "stone_shore snowy_beach birch_forest birch_forest_hills dark_forest snowy_taiga "
			+ "snowy_taiga_hills giant_tree_taiga giant_tree_taiga_hills wooded_mountains savanna "
			+ "savanna_plateau badlands wooded_badlands_plateau badlands_plateau warm_ocean "
			+ "lukewarm_ocean cold_ocean deep_warm_ocean deep_lukewarm_ocean deep_cold_ocean "
			+ "deep_frozen_ocean sunflower_plains desert_lakes gravelly_mountains flower_forest "
			+ "taiga_mountains swamp_hills ice_spikes modified_jungle modified_jungle_edge "
			+ "tall_birch_forest tall_birch_hills dark_forest_hills snowy_taiga_mountains "
			+ "giant_spruce_taiga giant_spruce_taiga_hills modified_gravelly_mountains "
			+ "shattered_savanna shattered_savanna_plateau eroded_badlands "
			+ "modified_wooded_badlands_plateau modified_badlands_plateau";
	private static final String BOP_OVERWORLD =
			"bamboo_grove bayou bog boreal_forest cherry_blossom_grove clover_patch cold_desert "
			+ "coniferous_forest crag dead_forest dryland dune_beach field fir_clearing floodplain "
			+ "forested_field fungal_jungle glowing_grotto grassland highland highland_moor jade_cliffs "
			+ "lavender_field lavender_forest lush_desert lush_savanna maple_woods marsh mediterranean_forest "
			+ "muskeg mystic_grove old_growth_dead_forest old_growth_woodland ominous_woods orchard origin_valley "
			+ "pasture prairie pumpkin_patch rainbow_hills rainforest redwood_forest rocky_rainforest "
			+ "rocky_shrubland scrubland seasonal_forest shrubland snowy_coniferous_forest snowy_fir_clearing "
			+ "snowy_maple_woods tropics tundra volcano volcanic_plains wasteland wetland wooded_scrubland "
			+ "wooded_wasteland woodland";

	private static final String BYG_OVERWORLD =
			"allium_fields amaranth_fields araucaria_savanna aspen_forest atacama_desert autumnal_valley "
			+ "baobab_savanna bayou black_forest borealis_grove canadian_shield cherry_blossom_forest cika_woods "
			+ "coniferous_forest crag_gardens cypress_swamplands lush_stacks dead_sea dacite_ridges windswept_dunes "
			+ "windswept_desert ebony_woods forgotten_forest temperate_grove guiana_shield howling_peaks "
			+ "jacaranda_forest maple_taiga coconino_meadow mojave_desert cardinal_tundra orchard prairie "
			+ "red_oak_forest red_rock_valley rose_fields autumnal_forest autumnal_taiga shattered_glacier "
			+ "firecracker_shrubland sierra_badlands skyris_vale redwood_thicket frosted_taiga "
			+ "frosted_coniferous_forest fragment_forest tropical_rainforest twilight_meadow "
			+ "weeping_witch_forest white_mangrove_marshes temperate_rainforest zelkova_forest windswept_beach "
			+ "rainbow_beach basalt_barrera dacite_shore";

	@Test
	void mapsEveryAuditedBiomesOPlentyOverworldBiome() {
		assertMapped(GeomeConfig.defaultBiomeRules(), "biomesoplenty", BOP_OVERWORLD);
	}

	@Test
	void mapsEveryAuditedBiomesYoullGoOverworldBiome() {
		assertMapped(GeomeConfig.defaultBiomeRules(), "byg", BYG_OVERWORLD);
	}

	@Test
	void doesNotMapKnownNetherOrEndBiomes() {
		JsonObject defaults = GeomeConfig.defaultBiomeRules();
		Set<String> expectedMinecraft = Arrays.stream(MINECRAFT_113_OVERWORLD.split(" "))
				.map(path -> "minecraft:" + path).collect(Collectors.toSet());
		Set<String> actualMinecraft = defaults.entrySet().stream().map(java.util.Map.Entry::getKey)
				.filter(id -> id.startsWith("minecraft:")).collect(Collectors.toSet());
		assertEquals(expectedMinecraft, actualMinecraft,
				"vanilla defaults must use exactly the Forge 14 / Minecraft 1.12.2 biome IDs");
		for (String id : new String[] { "biomesoplenty:crystalline_chasm", "biomesoplenty:erupting_inferno",
				"biomesoplenty:spider_nest", "biomesoplenty:undergrowth", "biomesoplenty:visceral_heap",
				"biomesoplenty:withered_abyss", "byg:brimstone_caverns", "byg:magma_wastes",
				"byg:nightshade_forest", "byg:cryptic_wastes" }) {
			assertFalse(defaults.has(id), () -> "Non-Overworld biome must not be mapped: " + id);
		}
	}

	private static void assertMapped(JsonObject defaults, String namespace, String paths) {
		Arrays.stream(paths.split(" ")).forEach(path -> {
			String id = namespace + ":" + path;
			assertTrue(defaults.has(id), () -> "Missing audited biome default: " + id);
		});
	}
}
