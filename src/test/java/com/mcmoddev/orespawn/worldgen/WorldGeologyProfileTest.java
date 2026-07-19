package com.mcmoddev.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Algorithm;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;

import org.junit.jupiter.api.Test;

class WorldGeologyProfileTest {
	@Test
	void schemaOneMigrationPreservesShapeAndSnapshotsGlobalGeology() {
		JsonObject global = completeGlobalFixture();
		WorldGeologyProfile fallback = WorldGeologyProfile.fromGlobalConfig(global, GeologyMode.GEOME, true);

		JsonObject legacy = new JsonObject();
		legacy.addProperty("schema_version", 1);
		legacy.addProperty("geology_mode", "legacy");
		legacy.addProperty("place_crude_oil", false);
		JsonObject formations = fallback.toFormationJson();
		formations.addProperty("algorithm", "sky_v1");
		formations.addProperty("horizontal_size", "custom");
		legacy.add("formations", formations);

		WorldGeologyProfile migrated = WorldGeologyProfile.fromJson(legacy, fallback);
		JsonObject result = migrated.toJson();
		assertEquals(WorldGeologyProfile.SCHEMA_VERSION, result.get("schema_version").getAsInt());
		assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
		assertEquals(Algorithm.SKY_V1, migrated.algorithm());
		assertEquals(Preset.CUSTOM, migrated.horizontalSize());
		assertTrue(result.has("geomes"));
		assertTrue(result.has("rocks"));
		assertTrue(result.has("ores"));
		assertTrue(result.has("oil"));
	}

	@Test
	void schemaTwoRoundTripKeepsExternalAndProviderData() {
		JsonObject root = completeGlobalFixture();
		root.addProperty("schema_version", 2);
		root.remove("terrain_dimensions");
		root.remove("providers");
		root.getAsJsonObject("rocks").add("examplemod:slate", rockFixture());
		JsonObject provider = new JsonObject();
		provider.addProperty("provider_revision", 3);
		root.getAsJsonObject("ore_providers").add("basemetals", provider);

		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.fromGlobalConfig(completeGlobalFixture(), GeologyMode.GEOME, true));
		JsonObject result = profile.toJson();
		assertTrue(result.getAsJsonObject("rocks").has("examplemod:slate"));
		assertEquals(3, result.getAsJsonObject("ore_providers")
				.getAsJsonObject("basemetals").get("provider_revision").getAsInt());
		assertEquals(WorldGeologyProfile.SCHEMA_VERSION, result.get("schema_version").getAsInt());
		assertTrue(result.has("terrain_dimensions"));
		assertTrue(result.has("providers"));
	}

	@Test
	void vanillaOreTakeoverRoundTripIsOptIn() {
		JsonObject root = completeGlobalFixture();
		root.addProperty("manage_vanilla_ores", true);
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(true));

		assertTrue(profile.manageVanillaOres());
		assertTrue(profile.toJson().get("manage_vanilla_ores").getAsBoolean());
		assertFalse(WorldGeologyProfile.recommended(true).manageVanillaOres());
	}

	@Test
	void oreDefaultsMergeAddsMissingEntriesWithoutOverwritingPackChoices() {
		JsonObject original = new JsonObject();
		JsonObject originalOres = new JsonObject();
		JsonObject customizedCoal = new JsonObject();
		customizedCoal.addProperty("enabled", false);
		originalOres.add("minecraft:coal_ore", customizedCoal);
		original.add("ores", originalOres);

		JsonObject defaults = new JsonObject();
		JsonObject defaultOres = new JsonObject();
		JsonObject defaultCoal = new JsonObject();
		defaultCoal.addProperty("enabled", true);
		defaultOres.add("minecraft:coal_ore", defaultCoal);
		defaultOres.add("minecraft:iron_ore", new JsonObject());
		defaults.add("ores", defaultOres);

		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, defaults);
		assertFalse(refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.get("enabled").getAsBoolean());
		assertTrue(refreshed.getAsJsonObject("ores").has("minecraft:iron_ore"));
		assertFalse(refreshed.get("manage_vanilla_ores").getAsBoolean());
		assertEquals(4, refreshed.get("ore_defaults_revision").getAsInt());
	}

	@Test
	void oreDefaultsUpgradeUntouchedClusterRules() {
		JsonObject original = oreDefaultsFixture(20.0D);
		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		JsonObject coal = refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");

		assertEquals(0, coal.get("min_y").getAsInt());
		assertEquals(96, coal.get("max_y").getAsInt());
		assertEquals(12.0D, coal.get("frequency").getAsDouble());
	}

	@Test
	void oreDefaultsUpgradeRevisionTwoClusterRules() {
		JsonObject original = oreDefaultsFixture(6.0D);
		original.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("max_y", 96);
		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		JsonObject coal = refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");

		assertEquals(12.0D, coal.get("frequency").getAsDouble());
	}

	@Test
	void oreDefaultsUpgradeUntouchedVeinRules() {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", -64);
		rule.addProperty("max_y", 256);
		rule.addProperty("frequency", 20.0D);
		rule.addProperty("quantity", 9);
		rule.addProperty("pattern", "vein");
		JsonObject iron = objectWith("dimensions", objectWith("minecraft:overworld", rule));
		JsonObject original = objectWith("ores", objectWith("minecraft:iron_ore", iron));
		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);

		assertEquals(34.0D, refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:iron_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());
	}

	@Test
	void oreDefaultsUpgradePreservesCustomizedClusterRules() {
		JsonObject original = oreDefaultsFixture(7.5D);
		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		JsonObject coal = refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");

		assertEquals(256, coal.get("max_y").getAsInt());
		assertEquals(7.5D, coal.get("frequency").getAsDouble());
	}

	@Test
	void orePatternNamesRejectUnknownValues() {
		assertEquals(OrePattern.VEIN, OrePattern.fromConfigName("VEIN"));
		assertEquals(OrePattern.CLUSTER, OrePattern.fromConfigName("cluster"));
		assertEquals(OrePattern.CLOUD, OrePattern.fromConfigName(" cloud "));
		assertEquals(OreHeightDistribution.TRIANGLE,
				OreHeightDistribution.fromConfigName("triangle"));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> OrePattern.fromConfigName("cascading_chunk_cache"));
	}

	@Test
	void legacyBuiltInGeomeNamesNormalizeWithoutChangingExternalIds() {
		assertEquals("orespawn:stable_craton", GeomeConfig.normalizeGeomeName("stable_craton"));
		assertEquals("orespawn:stable_craton", GeomeConfig.normalizeGeomeName("orespawn:stable_craton"));
		assertEquals("examplemod:crystal_basin", GeomeConfig.normalizeGeomeName("examplemod:crystal_basin"));
	}

	@Test
	void aliasDefaultsRepairUsesVanillaKeysWithoutChangingRockOrderOrTraits() {
		JsonObject original = new JsonObject();
		JsonObject rocks = new JsonObject();
		JsonObject andesite = rockFixture();
		andesite.addProperty("weight", 2.25D);
		rocks.add("orespawn:andesite", andesite);
		rocks.add("orespawn:basaltic_glass", rockFixture());
		rocks.add("orespawn:diorite", rockFixture());
		original.add("rocks", rocks);

		JsonObject defaults = new JsonObject();
		JsonObject aliases = new JsonObject();
		aliases.addProperty("orespawn:andesite", "minecraft:andesite");
		aliases.addProperty("orespawn:diorite", "minecraft:diorite");
		defaults.add("worldgen_aliases", aliases);

		JsonObject repaired = GeomeConfig.refreshWorldgenAliasDefaults(original, defaults);
		JsonObject repairedRocks = repaired.getAsJsonObject("rocks");
		assertEquals(Arrays.asList("minecraft:andesite", "orespawn:basaltic_glass", "minecraft:diorite"),
				new ArrayList<>(repairedRocks.keySet()));
		assertEquals(2.25D, repairedRocks.getAsJsonObject("minecraft:andesite").get("weight").getAsDouble());
		assertFalse(repairedRocks.has("orespawn:andesite"));
		assertTrue(repaired.getAsJsonObject("worldgen_aliases").has("orespawn:andesite"));
		assertEquals(1, repaired.get("worldgen_alias_defaults_revision").getAsInt());
	}

	private static JsonObject completeGlobalFixture() {
		JsonObject root = WorldGeologyProfile.recommended(true).toJson();
		root.add("geomes", objectWith("stable_craton", new JsonObject()));
		root.add("biomes", new JsonObject());
		root.add("biome_dictionary", new JsonObject());
		root.add("worldgen_aliases", new JsonObject());
		root.add("rocks", objectWith("minecraft:stone", rockFixture()));
		root.add("ores", new JsonObject());
		root.add("oil", new JsonObject());
		root.add("cyano", new JsonObject());
		root.add("ore_providers", new JsonObject());
		root.add("providers", new JsonObject());
		root.add("terrain_dimensions", objectWith("minecraft:overworld", new JsonObject()));
		return root;
	}

	private static JsonObject rockFixture() {
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("family", "sedimentary");
		rock.addProperty("weight", 1.0D);
		return rock;
	}

	private static JsonObject oreDefaultsFixture(double frequency) {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", 256);
		rule.addProperty("frequency", frequency);
		rule.addProperty("quantity", 17);
		rule.addProperty("pattern", "cluster");
		JsonObject dimensions = objectWith("minecraft:overworld", rule);
		JsonObject coal = objectWith("dimensions", dimensions);
		return objectWith("ores", objectWith("minecraft:coal_ore", coal));
	}

	private static JsonObject objectWith(String key, JsonObject value) {
		JsonObject result = new JsonObject();
		result.add(key, value);
		return result;
	}
}
