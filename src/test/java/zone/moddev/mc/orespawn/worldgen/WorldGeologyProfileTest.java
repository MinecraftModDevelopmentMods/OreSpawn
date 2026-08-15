package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Algorithm;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldGeologyProfileTest {
	@Test
	void schemaThreeOilMigratesToNamedFluidDeposit() {
		JsonObject legacy = completeGlobalFixture();
		legacy.addProperty("schema_version", 3);
		legacy.addProperty("place_crude_oil", true);
		legacy.remove("place_fluid_deposits");
		legacy.remove("fluid_deposits");
		JsonObject oil = new JsonObject();
		oil.addProperty("block", "mineralogy:crude_oil");
		oil.addProperty("min_y", -48);
		oil.addProperty("max_y", 48);
		oil.addProperty("frequency", 0.08D);
		oil.addProperty("min_radius", 5);
		oil.addProperty("max_radius", 12);
		oil.addProperty("min_vertical_radius", 2);
		oil.addProperty("max_vertical_radius", 5);
		oil.addProperty("max_lobes", 4);
		oil.addProperty("min_solid_cover", 2);
		legacy.add("oil", oil);

		WorldGeologyProfile migrated = WorldGeologyProfile.fromJson(legacy,
				WorldGeologyProfile.recommended(false));
		JsonObject result = migrated.toJson();
		assertTrue(result.get("place_fluid_deposits").getAsBoolean());
		assertTrue(result.getAsJsonObject("fluid_deposits")
				.has("mineralogy:fluid_deposit/crude_oil"));
		assertFalse(result.has("place_crude_oil"));
		assertFalse(result.has("oil"));
	}

	@Test
	void airOilSentinelDoesNotCreateAFluidRule() {
		JsonObject legacy = completeGlobalFixture();
		legacy.remove("fluid_deposits");
		JsonObject oil = new JsonObject();
		oil.addProperty("block", "minecraft:air");
		legacy.add("oil", oil);
		WorldGeologyProfile migrated = WorldGeologyProfile.fromJson(legacy,
				WorldGeologyProfile.recommended(false));
		assertEquals(0, migrated.fluidDepositCount());
	}

	@Test
	void schemaThreeWorldMigrationBacksUpAndPersistsFluidRule(@TempDir Path temporaryDirectory)
			throws IOException {
		JsonObject legacy = completeGlobalFixture();
		legacy.addProperty("schema_version", 3);
		legacy.addProperty("ore_defaults_revision", GeomeConfig.oreDefaultsRevision());
		legacy.remove("place_fluid_deposits");
		legacy.remove("fluid_deposits");
		legacy.addProperty("place_crude_oil", true);
		JsonObject oil = new JsonObject();
		oil.addProperty("block", "mineralogy:crude_oil");
		oil.addProperty("min_y", -48);
		oil.addProperty("max_y", 48);
		legacy.add("oil", oil);
		Path profilePath = temporaryDirectory.resolve("orespawn-worldgen.json");
		Files.write(profilePath, legacy.toString().getBytes(StandardCharsets.UTF_8));

		WorldGeologyProfile migrated = WorldGeologyProfileManager.readProfile(profilePath,
				WorldGeologyProfile.fromGlobalConfig(completeGlobalFixture(), GeologyMode.GEOME, false));

		assertTrue(Files.exists(temporaryDirectory.resolve("orespawn-worldgen.v3.bak")));
		assertTrue(migrated.toJson().getAsJsonObject("fluid_deposits")
				.has("mineralogy:fluid_deposit/crude_oil"));
		JsonObject persisted = new JsonParser().parse(new String(Files.readAllBytes(profilePath),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(WorldGeologyProfile.SCHEMA_VERSION,
				persisted.get("schema_version").getAsInt());
		assertFalse(persisted.has("oil"));
	}

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
		assertTrue(result.has("fluid_deposits"));
		assertFalse(result.has("oil"));
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
		assertEquals(GeomeConfig.oreDefaultsRevision(),
				refreshed.get("ore_defaults_revision").getAsInt());
	}

	@Test
	void oreDefaultsUpgradeUntouchedClusterRules() {
		JsonObject original = oreDefaultsFixture(20.0D);
		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		JsonObject coal = refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");

		assertEquals(0, coal.get("min_y").getAsInt());
		assertEquals(96, coal.get("max_y").getAsInt());
		assertEquals(6.27D, coal.get("frequency").getAsDouble());
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

		assertEquals(6.27D, coal.get("frequency").getAsDouble());
	}

	@Test
	void worldOreDefaultsUpgradeDoesNotRestoreRemovedEntries() {
		JsonObject original = oreDefaultsFixture(12.0D);
		original.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("max_y", 96);
		original.addProperty("ore_defaults_revision", 6);

		JsonObject refreshed = GeomeConfig.refreshWorldOreDefaults(original);
		JsonObject ores = refreshed.getAsJsonObject("ores");
		assertEquals(6.27D, ores.getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());
		assertFalse(ores.has("minecraft:iron_ore"));
		assertEquals(GeomeConfig.oreDefaultsRevision(),
				refreshed.get("ore_defaults_revision").getAsInt());
	}

	@Test
	void worldOreDefaultsRemoveUntouchedLegacyMineralogyDuplicates() {
		JsonObject original = objectWith("ores", new JsonObject());
		JsonObject ores = original.getAsJsonObject("ores");
		JsonObject canonical = oreDefaultsFixture(12.0D).getAsJsonObject("ores")
				.getAsJsonObject("minecraft:coal_ore");
		canonical.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("max_y", 96);
		ores.add("minecraft:coal_ore", canonical);
		ores.add("mineralogy:ore/minecraft/coal_ore", canonical.deepCopy());

		JsonObject refreshed = GeomeConfig.refreshWorldOreDefaults(original);
		assertFalse(refreshed.getAsJsonObject("ores")
				.has("mineralogy:ore/minecraft/coal_ore"));
		assertEquals(6.27D, refreshed.getAsJsonObject("ores")
				.getAsJsonObject("minecraft:coal_ore").getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld").get("frequency").getAsDouble());
	}

	@Test
	void worldOreDefaultsMoveCustomizedLegacyRuleOntoCanonicalId() {
		JsonObject original = objectWith("ores", new JsonObject());
		JsonObject ores = original.getAsJsonObject("ores");
		JsonObject canonical = oreDefaultsFixture(12.0D).getAsJsonObject("ores")
				.getAsJsonObject("minecraft:coal_ore");
		canonical.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("max_y", 96);
		ores.add("minecraft:coal_ore", canonical);
		JsonObject customizedLegacy = canonical.deepCopy();
		customizedLegacy.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("frequency", 2.75D);
		customizedLegacy.addProperty("orphaned_provider", true);
		ores.add("mineralogy:ore/minecraft/coal_ore", customizedLegacy);

		JsonObject refreshed = GeomeConfig.refreshWorldOreDefaults(original);
		JsonObject migrated = refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore");
		assertEquals(2.75D, migrated.getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld").get("frequency").getAsDouble());
		assertFalse(migrated.has("orphaned_provider"));
		assertFalse(refreshed.getAsJsonObject("ores")
				.has("mineralogy:ore/minecraft/coal_ore"));
	}

	@Test
	void worldProfileRefreshPreservesOriginalAndCustomRules(@TempDir Path temporaryDirectory)
			throws IOException {
		JsonObject root = completeGlobalFixture();
		root.addProperty("ore_defaults_revision", 6);
		JsonObject ores = root.getAsJsonObject("ores");
		JsonObject untouchedCoal = oreDefaultsFixture(12.0D).getAsJsonObject("ores")
				.getAsJsonObject("minecraft:coal_ore");
		untouchedCoal.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("max_y", 96);
		ores.add("minecraft:coal_ore", untouchedCoal);
		JsonObject customIronRule = new JsonObject();
		customIronRule.addProperty("min_y", -24);
		customIronRule.addProperty("max_y", 80);
		customIronRule.addProperty("frequency", 7.5D);
		customIronRule.addProperty("quantity", 5);
		customIronRule.addProperty("pattern", "vein");
		ores.add("minecraft:iron_ore", objectWith("dimensions",
				objectWith("minecraft:overworld", customIronRule)));

		Path profilePath = temporaryDirectory.resolve("orespawn-worldgen.json");
		Files.write(profilePath, root.toString().getBytes(StandardCharsets.UTF_8));
		WorldGeologyProfile fallback = WorldGeologyProfile.fromGlobalConfig(
				completeGlobalFixture(), GeologyMode.GEOME, true);

		WorldGeologyProfile loaded = WorldGeologyProfileManager.readProfile(profilePath, fallback);
		Path backupPath = temporaryDirectory.resolve("orespawn-worldgen.pre-ore-revision-10.bak");
		assertTrue(Files.exists(backupPath));
		JsonObject backup = new JsonParser().parse(new String(Files.readAllBytes(backupPath),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(12.0D, backup.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());

		JsonObject result = loaded.toJson();
		assertEquals(6.27D, result.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());
		assertEquals(7.5D, result.getAsJsonObject("ores").getAsJsonObject("minecraft:iron_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());
		JsonObject persisted = new JsonParser().parse(new String(Files.readAllBytes(profilePath),
				StandardCharsets.UTF_8)).getAsJsonObject();
		assertEquals(GeomeConfig.oreDefaultsRevision(),
				persisted.get("ore_defaults_revision").getAsInt());
	}

	@Test
	void os404GlobalOnlyProfilePreservesCustomValuesAndProviderDefinitions() {
		JsonObject global = completeGlobalFixture();
		global.getAsJsonObject("formations").addProperty("edge_irregularity", "custom");
		global.getAsJsonObject("formations").getAsJsonObject("custom")
				.addProperty("edge_amplitude", 37.25D);
		JsonObject provider = new JsonObject();
		provider.addProperty("provider_revision", 44);
		global.getAsJsonObject("providers").add("example:rocks", provider);

		JsonObject result = WorldGeologyProfile.fromGlobalConfig(global,
				GeologyMode.GEOME, false).toJson();

		assertEquals(37.25D, result.getAsJsonObject("formations")
				.getAsJsonObject("custom").get("edge_amplitude").getAsDouble());
		assertEquals(44, result.getAsJsonObject("providers")
				.getAsJsonObject("example:rocks").get("provider_revision").getAsInt());
	}

	@Test
	void os404WorldProfileWinsOverGlobalAndReloadsByteStable(@TempDir Path temporaryDirectory)
			throws IOException {
		JsonObject global = completeGlobalFixture();
		global.getAsJsonObject("formations").getAsJsonObject("custom")
				.addProperty("edge_amplitude", 91.0D);
		JsonObject world = completeGlobalFixture();
		world.getAsJsonObject("formations").addProperty("edge_irregularity", "custom");
		world.getAsJsonObject("formations").getAsJsonObject("custom")
				.addProperty("edge_amplitude", 23.5D);
		JsonObject provider = new JsonObject();
		provider.addProperty("provider_revision", 17);
		world.getAsJsonObject("providers").add("example:world_provider", provider);
		Path profilePath = temporaryDirectory.resolve("orespawn-worldgen.json");
		Files.write(profilePath, world.toString().getBytes(StandardCharsets.UTF_8));
		WorldGeologyProfile fallback = WorldGeologyProfile.fromGlobalConfig(global,
				GeologyMode.GEOME, true);

		WorldGeologyProfile first = WorldGeologyProfileManager.readProfile(profilePath, fallback);
		byte[] persisted = Files.readAllBytes(profilePath);
		WorldGeologyProfile second = WorldGeologyProfileManager.readProfile(profilePath, fallback);

		assertEquals(23.5D, first.toJson().getAsJsonObject("formations")
				.getAsJsonObject("custom").get("edge_amplitude").getAsDouble());
		assertEquals(17, first.toJson().getAsJsonObject("providers")
				.getAsJsonObject("example:world_provider").get("provider_revision").getAsInt());
		assertEquals(first.toJson(), second.toJson());
		assertTrue(Arrays.equals(persisted, Files.readAllBytes(profilePath)));
	}

	@Test
	void oreDefaultsUpgradeCanonicalPluralPatternNames() {
		JsonObject original = oreDefaultsFixture(12.0D);
		JsonObject rule = original.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", 96);
		rule.addProperty("pattern", "clusters");

		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		assertEquals(6.27D, refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:coal_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.get("frequency").getAsDouble());
	}

	@Test
	void oreDefaultsUpgradeUntouchedNetherRules() {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", 127);
		rule.addProperty("frequency", 16.0D);
		rule.addProperty("quantity", 14);
		rule.addProperty("pattern", "vein");
		JsonObject quartz = objectWith("dimensions", objectWith("minecraft:the_nether", rule));
		JsonObject original = objectWith("ores", objectWith("minecraft:nether_quartz_ore", quartz));

		JsonObject refreshed = GeomeConfig.refreshOreDefaults(original, original);
		assertEquals(11.2D, refreshed.getAsJsonObject("ores")
				.getAsJsonObject("minecraft:nether_quartz_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether")
				.get("frequency").getAsDouble());
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

		assertEquals(25.5D, refreshed.getAsJsonObject("ores").getAsJsonObject("minecraft:iron_ore")
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
	void worldOreDefaultsUpgradeVanillaVisibilityRulesWithoutChangingCustomizedRules() {
		JsonObject redstoneRule = oreRule(-64, 15, 4.7D, 8, "vein", "triangle", 0.0D);
		JsonObject diamondRule = oreRule(-64, 16, 1.8D, 8, "cluster", "triangle", 0.0D);
		JsonObject customDiamondRule = diamondRule.deepCopy();
		customDiamondRule.addProperty("discard_chance_on_air_exposure", 0.25D);
		JsonObject ores = new JsonObject();
		ores.add("minecraft:redstone_ore", objectWith("dimensions",
				objectWith("minecraft:overworld", redstoneRule)));
		ores.add("minecraft:diamond_ore", objectWith("dimensions",
				objectWith("minecraft:overworld", customDiamondRule)));
		JsonObject original = objectWith("ores", ores);
		original.addProperty("ore_defaults_revision", 8);

		JsonObject refreshed = GeomeConfig.refreshWorldOreDefaults(original);
		JsonObject migratedRedstone = refreshed.getAsJsonObject("ores")
				.getAsJsonObject("minecraft:redstone_ore").getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld");
		JsonObject preservedDiamond = refreshed.getAsJsonObject("ores")
				.getAsJsonObject("minecraft:diamond_ore").getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld");

		assertEquals(4.68D, migratedRedstone.get("frequency").getAsDouble());
		assertEquals("uniform_bottom_triangle",
				migratedRedstone.get("height_distribution").getAsString());
		assertEquals(0.78D,
				migratedRedstone.get("discard_chance_on_air_exposure").getAsDouble());
		assertEquals(1.8D, preservedDiamond.get("frequency").getAsDouble());
		assertEquals("triangle", preservedDiamond.get("height_distribution").getAsString());
		assertEquals(0.25D,
				preservedDiamond.get("discard_chance_on_air_exposure").getAsDouble());
	}

	@Test
	void worldOreDefaultsUpgradeUntouchedEmeraldCalibrationOnly() {
		JsonObject untouched = oreRule(-16, 128, 0.40D, 3, "cluster", "triangle", 0.65D);
		JsonObject custom = oreRule(-16, 128, 0.29D, 3, "cluster", "triangle", 0.65D);
		JsonObject ores = new JsonObject();
		ores.add("minecraft:emerald_ore", objectWith("dimensions",
				objectWith("minecraft:overworld", untouched)));
		JsonObject original = objectWith("ores", ores);
		original.addProperty("ore_defaults_revision", 9);

		JsonObject refreshed = GeomeConfig.refreshWorldOreDefaults(original);
		JsonObject migrated = refreshed.getAsJsonObject("ores")
				.getAsJsonObject("minecraft:emerald_ore").getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld");
		assertEquals(0.33D, migrated.get("frequency").getAsDouble());

		original.getAsJsonObject("ores").getAsJsonObject("minecraft:emerald_ore")
				.getAsJsonObject("dimensions").add("minecraft:overworld", custom);
		JsonObject preserved = GeomeConfig.refreshWorldOreDefaults(original)
				.getAsJsonObject("ores").getAsJsonObject("minecraft:emerald_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals(0.29D, preserved.get("frequency").getAsDouble());
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
		root.add("fluid_deposits", new JsonObject());
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

	private static JsonObject oreRule(int minY, int maxY, double frequency, int quantity,
			String pattern, String distribution, double discardChance) {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", minY);
		rule.addProperty("max_y", maxY);
		rule.addProperty("frequency", frequency);
		rule.addProperty("quantity", quantity);
		rule.addProperty("pattern", pattern);
		rule.addProperty("height_distribution", distribution);
		rule.addProperty("discard_chance_on_air_exposure", discardChance);
		return rule;
	}

	private static JsonObject objectWith(String key, JsonObject value) {
		JsonObject result = new JsonObject();
		result.add(key, value);
		return result;
	}
}
