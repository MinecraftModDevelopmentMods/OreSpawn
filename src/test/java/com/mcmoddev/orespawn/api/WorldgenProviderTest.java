package com.mcmoddev.orespawn.api;

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
	void serializesTypedSchemaTwoProvider() {
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
						.pattern(OrePattern.CLUSTER).hostFamily(GeologyFamily.METAMORPHIC)
						.hostBlock(id("minecraft:deepslate"), 0.75D)))
				.biome(id("minecraft:jagged_peaks"),
						Collections.singletonMap(id("orespawn:mountain_belt"), 2.0D))
				.build();

		JsonObject json = provider.toJson();
		assertEquals(2, json.get("schema_version").getAsInt());
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
		assertTrue(json.getAsJsonObject("biome_rules").has("minecraft:jagged_peaks"));

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
		WorldgenProvider.OilDefinition oil = WorldgenProvider.OilDefinition.builder()
				.yRange(-32, 24).attempts(0.05D).radius(10, 18)
				.verticalRadius(3, 8).maxLobes(5).minSolidCover(3).build();

		WorldgenProvider provider = WorldgenProvider.builder("examplemod", 2)
				.geome(id("examplemod:crystal_basin"), geome -> geome
						.baseWeight(0.8D).familyWeight(GeologyFamily.IGNEOUS_INTRUSIVE, 2.0D))
				.biome(id("examplemod:crystal_fields"), Collections.singletonMap(
						id("examplemod:crystal_basin"), 3.0D))
				.terrainDimension(dimension, terrain -> terrain
						.biomeNamespace("examplemod").hostTag(id("examplemod:base_stone")))
				.template(id("examplemod:huge_crystals"), template -> template
						.requiresMod("examplemod").formations(formations).oil(oil))
				.build();

		JsonObject json = provider.toJson();
		assertTrue(json.getAsJsonObject("geomes").has("examplemod:crystal_basin"));
		assertTrue(json.getAsJsonObject("biome_rules").has("examplemod:crystal_fields"));
		assertTrue(json.getAsJsonObject("terrain_dimensions").has(dimension.toString()));
		JsonObject template = json.getAsJsonObject("templates")
				.getAsJsonObject("examplemod:huge_crystals").getAsJsonObject("profile");
		assertEquals("huge", template.getAsJsonObject("formations")
				.get("horizontal_size").getAsString());
		assertEquals(3, template.getAsJsonObject("oil").get("min_solid_cover").getAsInt());
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

		GeologyProfileView view = new GeologyProfileView(root);
		root.getAsJsonObject("rocks").remove("minecraft:calcite");
		assertTrue(view.rockIds().contains(id("minecraft:calcite")));
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
	}

	private static ResourceLocation id(String value) {
		return new ResourceLocation(value);
	}
}
