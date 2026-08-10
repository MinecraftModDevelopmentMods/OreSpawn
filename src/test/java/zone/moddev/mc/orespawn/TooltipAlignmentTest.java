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
	private static final List<String> OVERVIEW_SCREENS = Arrays.asList(
			"AdvancedGeologySettingsScreen.java",
			"BlockAssignmentScreen.java",
			"BlockPickerScreen.java",
			"FluidDepositListScreen.java",
			"GeologyMaterialsScreen.java",
			"GeomeBiomeScreen.java",
			"GeomeEntryScreen.java",
			"NumericConfigScreen.java",
			"OreSpawnWorldSettingsScreen.java",
			"WeightMapScreen.java");
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
			"tooltip.orespawn.material.ice_block",
			"tooltip.orespawn.main.template",
			"tooltip.orespawn.main.recommended",
			"tooltip.orespawn.main.materials",
			"tooltip.orespawn.main.configure_strata",
			"tooltip.orespawn.main.biomes_materials",
			"tooltip.orespawn.main.advanced",
			"tooltip.orespawn.main.fluid_editor",
			"tooltip.orespawn.manage_vanilla_ores",
			"tooltip.orespawn.fluid_deposits",
			"tooltip.orespawn.advanced.formations",
			"tooltip.orespawn.advanced.cyano",
			"tooltip.orespawn.advanced.fluid_deposits",
			"tooltip.orespawn.numeric.stratum_wavelength",
			"tooltip.orespawn.numeric.family_region_wavelength",
			"tooltip.orespawn.numeric.vertical_thickness",
			"tooltip.orespawn.numeric.waviness_wavelength",
			"tooltip.orespawn.numeric.waviness_amplitude",
			"tooltip.orespawn.numeric.edge_wavelength",
			"tooltip.orespawn.numeric.edge_amplitude",
			"tooltip.orespawn.numeric.edge_octaves",
			"tooltip.orespawn.numeric.continuity",
			"tooltip.orespawn.numeric.geome_size",
			"tooltip.orespawn.numeric.rock_layer_noise",
			"tooltip.orespawn.numeric.rock_layer_thickness",
			"tooltip.orespawn.geome.base_weight",
			"tooltip.orespawn.geome.family_weight",
			"tooltip.orespawn.geome.entry_weight",
			"tooltip.orespawn.geome.biome_weight",
			"tooltip.orespawn.geome.tab.geomes",
			"tooltip.orespawn.geome.tab.biomes",
			"tooltip.orespawn.geome.tab.dictionary",
			"tooltip.orespawn.geome.new_id.geomes",
			"tooltip.orespawn.geome.new_id.biomes",
			"tooltip.orespawn.geome.new_id.dictionary",
			"tooltip.orespawn.material.tab.sedimentary",
			"tooltip.orespawn.material.tab.metamorphic",
			"tooltip.orespawn.material.tab.igneous",
			"tooltip.orespawn.material.tab.ores",
			"tooltip.orespawn.material.tab.unassigned",
			"tooltip.orespawn.material.show_all",
			"tooltip.orespawn.material.safe_only",
			"tooltip.orespawn.material.add_block",
			"tooltip.orespawn.picker.mod_filter",
			"tooltip.orespawn.assignment.rock_family",
			"tooltip.orespawn.assignment.ore",
			"tooltip.orespawn.fluid.add_deposit");

	@Test
	void detailedFormsDoNotReuseMultiControlGuideParagraphs() throws Exception {
		for (String screen : DETAILED_SCREENS) {
			String source = read(CLIENT_DIR.resolve(screen));
			assertFalse(source.contains("guide.orespawn."),
					screen + " must use control-specific tooltip text rather than a multi-control guide paragraph");
			assertTrue(source.contains("tooltip.orespawn.") || source.contains("external_pattern_read_only"),
					screen + " must retain explicit tooltip assignments");
		}
	}

	@Test
	void overviewControlsUseFocusedHelpAndHelpButtonDoesNotExplainItself() throws Exception {
		for (String screen : OVERVIEW_SCREENS) {
			String source = read(CLIENT_DIR.resolve(screen));
			assertFalse(source.contains("guide.orespawn."),
					screen + " must use control-specific help instead of guide paragraphs");
		}
		String main = read(CLIENT_DIR.resolve("OreSpawnWorldSettingsScreen.java"));
		assertFalse(main.contains("tooltip.orespawn.help"),
				"Help & Guide opens the guide and must not carry a self-describing tooltip");
	}

	@Test
	void customScreensDoNotUseUnrenderedNativeCycleTooltipsOnMinecraft116() throws Exception {
		try (java.util.stream.Stream<Path> files = Files.list(CLIENT_DIR)) {
			for (Path sourceFile : (Iterable<Path>) files.filter(path -> path.toString().endsWith("Screen.java"))::iterator) {
				String source = read(sourceFile);
				assertFalse(source.contains(".withTooltip("), sourceFile.getFileName()
						+ " must use OreSpawnScreenLayout.explain because Screen does not render CycleButton tooltips in 1.14.4");
			}
		}
	}

	@Test
	void everyDetailedControlTooltipHasEnglishText() throws Exception {
		JsonObject english;
		try (Reader reader = Files.newBufferedReader(ENGLISH, StandardCharsets.UTF_8)) {
			english = new JsonParser().parse(reader).getAsJsonObject();
		}
		for (String key : REQUIRED_TOOLTIPS) {
			assertTrue(english.has(key), key + " is missing from en_us.json");
			assertFalse(english.get(key).getAsString().trim().isEmpty(), key + " has no help text");
		}
	}

	private static String read(Path path) throws Exception {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}
}
