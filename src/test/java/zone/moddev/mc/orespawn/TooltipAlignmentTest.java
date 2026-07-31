package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class TooltipAlignmentTest {
	private static final Path CLIENT_DIR = Paths.get("src", "main", "java", "zone", "moddev", "mc",
			"orespawn", "client");
	private static final Path ENGLISH = Paths.get("src", "main", "resources", "assets", "orespawn",
			"lang", "en_us.json");
	private static final List<String> DETAILED_SCREENS = Arrays.asList(
			"BiomePlacementScreen.java",
			"BiomeWorldMaterialsScreen.java",
			"DimensionMaterialsScreen.java",
			"FluidDepositDimensionScreen.java",
			"FluidDepositEntryScreen.java",
			"OreDimensionScreen.java",
			"OreEntryScreen.java",
			"RockEntryScreen.java");
	private static final List<String> REQUIRED_TOOLTIPS = Arrays.asList(
			"tooltip.orespawn.enabled",
			"tooltip.orespawn.weight",
			"tooltip.orespawn.geome_weights",
			"tooltip.orespawn.host_family",
			"tooltip.orespawn.host_blocks",
			"tooltip.orespawn.host_tags",
			"tooltip.orespawn.fluid.dimension_settings",
			"tooltip.orespawn.fluid.available_dimension",
			"tooltip.orespawn.fluid.min_y",
			"tooltip.orespawn.fluid.max_y",
			"tooltip.orespawn.fluid.frequency",
			"tooltip.orespawn.fluid.min_radius",
			"tooltip.orespawn.fluid.max_radius",
			"tooltip.orespawn.fluid.min_vertical_radius",
			"tooltip.orespawn.fluid.max_vertical_radius",
			"tooltip.orespawn.fluid.max_lobes",
			"tooltip.orespawn.fluid.min_solid_cover",
			"tooltip.orespawn.fluid.min_solid_shell",
			"tooltip.orespawn.fluid.biome_ids",
			"tooltip.orespawn.fluid.excluded_biome_ids",
			"tooltip.orespawn.fluid.biome_dictionary",
			"tooltip.orespawn.fluid.excluded_biome_dictionary",
			"tooltip.orespawn.ore.min_y",
			"tooltip.orespawn.ore.max_y",
			"tooltip.orespawn.ore.frequency",
			"tooltip.orespawn.ore.min_quantity",
			"tooltip.orespawn.ore.max_quantity",
			"tooltip.orespawn.ore.discard_air_exposure",
			"tooltip.orespawn.ore.pattern",
			"tooltip.orespawn.ore.height_distribution",
			"tooltip.orespawn.ore.spread",
			"tooltip.orespawn.ore.vertical_spread",
			"tooltip.orespawn.ore.node_size",
			"tooltip.orespawn.rock.family",
			"tooltip.orespawn.rock.depth_peak",
			"tooltip.orespawn.rock.depth_spread",
			"tooltip.orespawn.rock.min_y",
			"tooltip.orespawn.rock.max_y",
			"tooltip.orespawn.rock.ore_replaceable",
			"tooltip.orespawn.biome.dimension",
			"tooltip.orespawn.biome.palette_enabled",
			"tooltip.orespawn.biome.mode",
			"tooltip.orespawn.biome.scope",
			"tooltip.orespawn.biome.region_size",
			"tooltip.orespawn.biome.entries",
			"tooltip.orespawn.biome.dimension_materials",
			"tooltip.orespawn.biome.geome_influences",
			"tooltip.orespawn.biome.similar_biomes",
			"tooltip.orespawn.biome.required_similar_biomes",
			"tooltip.orespawn.biome.min_temperature",
			"tooltip.orespawn.biome.max_temperature",
			"tooltip.orespawn.biome.min_downfall",
			"tooltip.orespawn.biome.max_downfall",
			"tooltip.orespawn.biome.top_block",
			"tooltip.orespawn.biome.filler_block",
			"tooltip.orespawn.biome.underwater_block",
			"tooltip.orespawn.biome.ceiling_block",
			"tooltip.orespawn.biome.filler_depth",
			"tooltip.orespawn.material.default_fluid",
			"tooltip.orespawn.material.deep_aquifer_fluid",
			"tooltip.orespawn.material.deep_aquifer_y",
			"tooltip.orespawn.material.snow_block",
			"tooltip.orespawn.material.ice_block");

	@Test
	void detailedFormsDoNotReuseMultiControlGuideParagraphs() throws Exception {
		for (String screen : DETAILED_SCREENS) {
			String source = Files.readString(CLIENT_DIR.resolve(screen), StandardCharsets.UTF_8);
			assertFalse(source.contains("guide.orespawn."),
					screen + " must use control-specific tooltip text rather than a multi-control guide paragraph");
			assertTrue(source.contains("tooltip.orespawn.") || source.contains("external_pattern_read_only"),
					screen + " must retain explicit tooltip assignments");
		}
	}

	@Test
	void everyDetailedControlTooltipHasEnglishText() throws Exception {
		JsonObject english;
		try (Reader reader = Files.newBufferedReader(ENGLISH, StandardCharsets.UTF_8)) {
			english = JsonParser.parseReader(reader).getAsJsonObject();
		}
		for (String key : REQUIRED_TOOLTIPS) {
			assertTrue(english.has(key), key + " is missing from en_us.json");
			assertFalse(english.get(key).getAsString().trim().isEmpty(), key + " has no help text");
		}
	}
}
