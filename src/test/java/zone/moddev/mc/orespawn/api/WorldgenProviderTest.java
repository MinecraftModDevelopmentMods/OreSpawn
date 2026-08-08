package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class WorldgenProviderTest {
	@Test
	void serializesTypedSchemaFourProvider() {
		ResourceLocation overworld = id("minecraft:overworld");
		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 4)
				.rock(id("examplemod:slate"), GeologyFamily.METAMORPHIC, rock -> rock
						.depth(20, 36).weight(1.25D).oreReplaceable(true))
				.ore(id("examplemod:tin_ore"), ore -> ore
						.output(id("examplemod:tin_ore"), 9.0D)
						.output(id("examplemod:rich_tin_ore"), 1.0D, -64, 24)
						.suppressVanilla(true).retrogen(false)
						.dimension(overworld, dimension -> dimension
						.yRange(-16, 96).attempts(6.5D).quantity(8)
						.pattern(OrePattern.CLUSTER)
						.heightDistribution(OreHeightDistribution.BOTTOM_TRIANGLE)
						.discardChanceOnAirExposure(0.75D)
						.hostFamily(GeologyFamily.METAMORPHIC)
						.hostBlock(id("minecraft:deepslate"), 0.75D)))
				.biome(id("minecraft:mountains"),
						Collections.singletonMap(id("orespawn:mountain_belt"), 2.0D))
				.build();

		JsonObject json = provider.toJson();
		assertEquals(4, json.get("schema_version").getAsInt());
		assertEquals("examplemod", json.get("provider_modid").getAsString());
		assertTrue(json.getAsJsonObject("rocks").has("examplemod:rock/examplemod/slate"));
		assertEquals("examplemod:slate", json.getAsJsonObject("rocks")
				.getAsJsonObject("examplemod:rock/examplemod/slate").get("block").getAsString());
		assertTrue(json.getAsJsonObject("ores").has("examplemod:ore/examplemod/tin_ore"));
		JsonObject ore = json.getAsJsonObject("ores").getAsJsonObject("examplemod:ore/examplemod/tin_ore");
		assertEquals(2, ore.getAsJsonArray("outputs").size());
		assertFalse(ore.get("retrogen").getAsBoolean());
		assertEquals(0.75D, ore.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.getAsJsonArray("host_blocks").get(0).getAsJsonObject().get("weight").getAsDouble());
		assertEquals("bottom_triangle", ore.getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld").get("height_distribution").getAsString());
		assertEquals(0.75D, ore.getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld")
				.get("discard_chance_on_air_exposure").getAsDouble());
		assertTrue(json.getAsJsonObject("biome_rules").has("minecraft:mountains"));

		json.getAsJsonObject("rocks").remove("examplemod:rock/examplemod/slate");
		assertTrue(provider.toJson().getAsJsonObject("rocks")
				.has("examplemod:rock/examplemod/slate"));
	}

	@Test
	void serializesCompleteDeclarativeProviderSurface() {
		ResourceLocation dimension = id("examplemod:crystal_caverns");
		WorldgenProvider.FormationDefinition formations = WorldgenProvider.FormationDefinition.builder()
				.horizontalSize(FormationPreset.HUGE)
				.waviness(FormationPreset.CUSTOM)
				.customValue("waviness_amplitude", 180.0D)
				.build();
		WorldgenProvider.FluidDepositDefinition brine = WorldgenProvider.FluidDepositDefinition.builder(
				id("examplemod:fluid_deposit/brine"), id("examplemod:brine"))
				.dimension(id("minecraft:overworld"), rule -> rule
						.yRange(-32, 24).attempts(0.05D).radius(10, 18)
						.verticalRadius(3, 8).maxLobes(5).minSolidCover(3).minSolidShell(2)
						.hostFamily(GeologyFamily.SEDIMENTARY).biomeDictionary("OCEAN"))
				.build();

		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 2)
				.geome(id("examplemod:crystal_basin"), geome -> geome
						.baseWeight(0.8D).familyWeight(GeologyFamily.IGNEOUS_INTRUSIVE, 2.0D))
				.biome(id("examplemod:crystal_fields"), Collections.singletonMap(
						id("examplemod:crystal_basin"), 3.0D))
				.terrainDimension(dimension, terrain -> terrain
						.biomeNamespace("examplemod").hostTag(id("examplemod:base_stone")))
				.fluidDeposit(brine)
				.template(id("examplemod:huge_crystals"), template -> template
						.requiresMod("examplemod").formations(formations).fluidDeposit(brine))
				.build();

		JsonObject json = provider.toJson();
		assertTrue(json.getAsJsonObject("geomes").has("examplemod:crystal_basin"));
		assertTrue(json.getAsJsonObject("biome_rules").has("examplemod:crystal_fields"));
		assertTrue(json.getAsJsonObject("terrain_dimensions").has(dimension.toString()));
		JsonObject template = json.getAsJsonObject("templates")
				.getAsJsonObject("examplemod:huge_crystals").getAsJsonObject("profile");
		assertEquals("huge", template.getAsJsonObject("formations")
				.get("horizontal_size").getAsString());
		assertEquals(3, template.getAsJsonObject("fluid_deposits")
				.getAsJsonObject("examplemod:fluid_deposit/brine")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("min_solid_cover").getAsInt());
		assertEquals(2, template.getAsJsonObject("fluid_deposits")
				.getAsJsonObject("examplemod:fluid_deposit/brine")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("min_solid_shell").getAsInt());
	}

	@Test
	void serializesBiomePaletteDimensionMaterialsAndAutoTemplate() {
		ResourceLocation overworld = id("minecraft:overworld");
		WorldgenProvider.BiomeSurfaceDefinition surface =
				WorldgenProvider.BiomeSurfaceDefinition.builder()
						.topBlock(id("minecraft:cake"))
						.fillerBlock(id("minecraft:white_wool"))
						.underwaterBlock(id("minecraft:sand"))
						.fillerDepth(4)
						.build();
		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
				.biomePalette(id("examplemod:palette/cake"), overworld, palette -> palette
						.mode(BiomePlacementMode.REPLACE)
						.scope(BiomeReplacementScope.MINECRAFT_ONLY)
						.regionSize(BiomeRegionSize.LARGE)
						.coverage(0.8D)
						.biome(id("minecraft:plains"), biome -> biome
								.weight(3.0D)
								.similarBiome(id("minecraft:forest"))
								.temperature(0.1D, 1.5D)
								.surface(surface)))
				.dimensionMaterials(id("examplemod:materials/overworld"), overworld,
						materials -> materials.defaultFluid(id("minecraft:water"))
								.deepAquiferFluid(id("minecraft:lava"), -54)
								.snowBlock(id("minecraft:snow_block"))
								.iceBlock(id("minecraft:ice")))
				.template(id("examplemod:cake"), template -> template
						.autoSelect(true).autoSelectPriority(50)
						.profile(profileDefaults()))
				.build();

		JsonObject json = provider.toJson();
		JsonObject palette = json.getAsJsonObject("biome_palettes")
				.getAsJsonObject("examplemod:palette/cake");
		assertEquals("replace", palette.get("mode").getAsString());
		assertEquals("large", palette.get("region_size").getAsString());
		assertEquals("minecraft:cake", palette.getAsJsonObject("biomes")
				.getAsJsonObject("minecraft:plains").getAsJsonObject("surface")
				.get("top_block").getAsString());
		JsonObject materials = json.getAsJsonObject("dimension_materials")
				.getAsJsonObject("examplemod:materials/overworld");
		assertEquals("minecraft:water", materials.get("default_fluid").getAsString());
		JsonObject template = json.getAsJsonObject("templates")
				.getAsJsonObject("examplemod:cake");
		assertTrue(template.get("auto_select").getAsBoolean());
		assertEquals(50, template.get("auto_select_priority").getAsInt());
	}

	@Test
	void serializesCodecBackedPatternWithoutLeakingMutableSettings() {
		JsonObject settings = new JsonObject();
		settings.addProperty("radius", 12);
		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
				.ore(id("examplemod:crystal_ore"), ore -> ore.dimension(id("minecraft:overworld"), dimension -> dimension
						.pattern(id("examplemod:sheet"), settings)
						.hostTag(id("minecraft:base_stone_overworld"))))
				.build();
		settings.addProperty("radius", 99);

		JsonObject pattern = provider.toJson().getAsJsonObject("ores")
				.getAsJsonObject("examplemod:ore/examplemod/crystal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.getAsJsonObject("pattern");
		assertEquals("examplemod:sheet", pattern.get("type").getAsString());
		assertEquals(12, pattern.getAsJsonObject("settings").get("radius").getAsInt());
	}

	@Test
	void rejectsDefinitionsOutsideProviderNamespace() {
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.builder("examplemod", 1)
				.rock(id("minecraft:not_owned"), id("minecraft:calcite"),
						GeologyFamily.METAMORPHIC, rock -> { })
				.build());
	}

	@Test
	void profileViewIsImmutableAndNamespaced() {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", 3);
		root.addProperty("selected_template", "examplemod:large_layers");
		JsonObject rocks = new JsonObject();
		rocks.add("minecraft:calcite", new JsonObject());
		root.add("rocks", rocks);
		root.add("ores", new JsonObject());
		root.add("terrain_dimensions", new JsonObject());
		JsonObject palettes = new JsonObject();
		palettes.add("examplemod:overworld", new JsonObject());
		root.add("biome_palettes", palettes);
		JsonObject materials = new JsonObject();
		materials.add("examplemod:overworld", new JsonObject());
		root.add("dimension_materials", materials);

		GeologyProfileView view = new GeologyProfileView(root);
		root.getAsJsonObject("rocks").remove("minecraft:calcite");
		assertTrue(view.rockIds().contains(id("minecraft:calcite")));
		assertTrue(view.biomePaletteIds().contains(id("examplemod:overworld")));
		assertTrue(view.dimensionMaterialIds().contains(id("examplemod:overworld")));
		assertEquals(id("examplemod:large_layers"), view.selectedTemplate().orElseThrow());
		assertThrows(UnsupportedOperationException.class,
				() -> view.rockIds().add(id("minecraft:stone")));
		assertFalse(view.toJson() == view.toJson());
	}

	@Test
	void rejectsInvalidOrePlacementEarly() {
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.OreDimensionDefinition
				.builder(id("minecraft:overworld")).attempts(-1.0D)
				.hostTag(id("minecraft:base_stone_overworld")).build());
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.OreDimensionDefinition
				.builder(id("minecraft:overworld")).discardChanceOnAirExposure(1.01D)
				.hostTag(id("minecraft:base_stone_overworld")).build());
	}

	@Test
	void serializesRangedQuantityAndBroadDimensionSelector() {
		WorldgenProvider.OreDimensionDefinition placement = WorldgenProvider.OreDimensionDefinition
				.builder(OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id())
				.yRange(0, 127).attempts(5.0D).quantityRange(4, 11)
				.hostTag(id("minecraft:stone_ore_replaceables")).build();
		assertEquals(4, placement.minQuantity());
		assertEquals(11, placement.maxQuantity());
		assertEquals(8, placement.quantity());

		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
				.ore(id("examplemod:copper_ore"), ore -> ore.dimensionSelector(
						OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END, placement))
				.build();
		JsonObject ore = provider.toJson().getAsJsonObject("ores")
				.getAsJsonObject("examplemod:ore/examplemod/copper_ore");
		assertFalse(ore.has("dimensions"));
		JsonObject rule = ore
				.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		assertEquals(4, rule.get("min_quantity").getAsInt());
		assertEquals(11, rule.get("max_quantity").getAsInt());
		assertFalse(rule.has("quantity"));
	}

	@Test
	void rejectsInvalidQuantityRangesEarly() {
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.OreDimensionDefinition
				.builder(id("minecraft:overworld")).quantityRange(12, 4)
				.hostTag(id("minecraft:base_stone_overworld")).build());
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.OreDimensionDefinition
				.builder(id("minecraft:overworld")).quantityRange(1, 65)
				.hostTag(id("minecraft:base_stone_overworld")).build());
	}

	@Test
	void rejectsInvalidFluidPlacementEarly() {
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.FluidDepositDimensionDefinition
				.builder(id("minecraft:overworld")).attempts(-1.0D)
				.hostTag(id("minecraft:stone_ore_replaceables")).build());
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.FluidDepositDimensionDefinition
				.builder(id("minecraft:overworld")).radius(12, 5)
				.hostTag(id("minecraft:stone_ore_replaceables")).build());
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.FluidDepositDimensionDefinition
				.builder(id("minecraft:overworld")).minSolidShell(-1)
				.hostTag(id("minecraft:stone_ore_replaceables")).build());
		assertThrows(IllegalStateException.class, () -> WorldgenProvider.FluidDepositDimensionDefinition
				.builder(id("minecraft:overworld")).build());
	}

	private static ResourceLocation id(String value) {
		return new ResourceLocation(value);
	}

	private static JsonObject profileDefaults() {
		JsonObject profile = new JsonObject();
		profile.addProperty("manage_vanilla_ores", true);
		return profile;
	}
}
