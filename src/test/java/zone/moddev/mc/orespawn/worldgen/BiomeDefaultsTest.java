package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class BiomeDefaultsTest {
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
