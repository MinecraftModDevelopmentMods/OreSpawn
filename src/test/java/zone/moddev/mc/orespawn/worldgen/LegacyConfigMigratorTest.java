package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyConfigMigratorTest {
	@TempDir Path temporary;

	@Test
	void migratesBaseMetalsOs3FixtureExactly() throws IOException {
		Path legacyDirectory = Files.createDirectories(temporary.resolve("orespawn3"));
		Files.writeString(legacyDirectory.resolve("basemetals.json"),
				new GsonBuilder().setPrettyPrinting().create().toJson(baseMetalsFixture()),
				StandardCharsets.UTF_8);
		JsonObject defaults = new JsonObject();
		defaults.add("ores", new JsonObject());

		JsonObject migrated = LegacyConfigMigrator.migrateIfNeeded(
				temporary.resolve("orespawn-worldgen.json"), defaults);

		assertNotNull(migrated);
		JsonObject ores = migrated.getAsJsonObject("ores");
		assertEquals(11, ores.size());
		int selectorRules = 0;
		for (String id : ores.keySet()) {
			JsonObject ore = ores.getAsJsonObject(id);
			JsonObject rule;
			if (ore.has("dimension_selectors")) {
				selectorRules++;
				rule = ore.getAsJsonObject("dimension_selectors")
						.getAsJsonObject("orespawn:all_except_nether_end");
			} else {
				rule = ore.getAsJsonObject("dimensions").entrySet().iterator().next()
						.getValue().getAsJsonObject();
			}
			assertEquals(4, rule.get("min_quantity").getAsInt());
			assertEquals(11, rule.get("max_quantity").getAsInt());
			assertFalse(rule.has("quantity"));
		}
		assertEquals(8, selectorRules);
		assertTrue(ore(ores, "coldiron_ore").getAsJsonObject("dimensions").has("minecraft:the_nether"));
		assertTrue(ore(ores, "adamantine_ore").getAsJsonObject("dimensions").has("minecraft:the_nether"));
		assertTrue(ore(ores, "starsteel_ore").getAsJsonObject("dimensions").has("minecraft:the_end"));
		assertEquals(127, rule(ore(ores, "coldiron_ore")).get("max_y").getAsInt());
		assertEquals(0.125D, rule(ore(ores, "platinum_ore")).get("frequency").getAsDouble());
		String upgradeReport = read(temporary.resolve("orespawn-upgrade-report.txt"));
		assertTrue(upgradeReport.contains("Spawn definitions imported: 11"));
		assertTrue(upgradeReport.contains("Original legacy configuration files were retained unchanged"));
	}

	@Test
	void defaultsLegacyMaximumTo255AndReportsQuantityClamping() throws IOException {
		Path legacyDirectory = Files.createDirectories(temporary.resolve("orespawn3"));
		JsonObject root = new JsonObject();
		root.addProperty("version", "2.0");
		JsonObject spawns = new JsonObject();
		JsonObject spawn = spawn("test_ore", null, 100, 80, 1.0D, 0, null);
		spawns.add("test_ore", spawn);
		spawns.add("fixed_ore", spawn("fixed_ore", null, 8, 0, 1.0D, 0, 16));
		spawns.add("empty_height_ore", spawn("empty_height_ore", null, 8, 0, 1.0D, 32, 32));
		root.add("spawns", spawns);
		Files.writeString(legacyDirectory.resolve("clamped.json"), root.toString(), StandardCharsets.UTF_8);
		JsonObject defaults = new JsonObject();
		defaults.add("ores", new JsonObject());

		JsonObject migrated = LegacyConfigMigrator.migrateIfNeeded(
				temporary.resolve("orespawn-worldgen.json"), defaults);
		JsonObject migratedOres = migrated.getAsJsonObject("ores");
		JsonObject placement = rule(ore(migratedOres, "clamped", "test_ore"));
		assertEquals(2, migratedOres.size());
		assertEquals(255, placement.get("max_y").getAsInt());
		assertEquals(20, placement.get("min_quantity").getAsInt());
		assertEquals(64, placement.get("max_quantity").getAsInt());
		JsonObject fixed = rule(ore(migratedOres, "clamped", "fixed_ore"));
		assertEquals(8, fixed.get("quantity").getAsInt());
		assertFalse(fixed.has("min_quantity"));
		String report = Files.readString(temporary.resolve("orespawn-migration/migration-report.txt"));
		assertTrue(report.contains("Clamped legacy quantity"));
		assertTrue(report.contains("empty legacy height range"));
	}

	@Test
	void mapsUniqueInstalledProviderOutputWithoutDuplicatingAndPreservesUserValues() throws IOException {
		Path legacyDirectory = Files.createDirectories(temporary.resolve("orespawn3"));
		JsonObject root = new JsonObject();
		root.addProperty("version", "2.0");
		JsonObject custom = spawn("copper_ore", null, 12, 2, 7.5D, -16, 113);
		custom.addProperty("enabled", false);
		JsonObject spawns = new JsonObject();
		spawns.add("copper_ore", custom);
		root.add("spawns", spawns);
		Files.writeString(legacyDirectory.resolve("basemetals.json"), root.toString(), StandardCharsets.UTF_8);

		JsonObject defaults = new JsonObject();
		JsonObject ores = new JsonObject();
		JsonObject providerDefault = new JsonObject();
		providerDefault.addProperty("block", "basemetals:copper_ore");
		providerDefault.addProperty("enabled", true);
		ores.add("basemetals:ore/copper", providerDefault);
		defaults.add("ores", ores);

		JsonObject migrated = LegacyConfigMigrator.migrateIfNeeded(
				temporary.resolve("orespawn-worldgen.json"), defaults,
				(owner, output) -> owner.equals("basemetals") && output.equals("basemetals:copper_ore")
						? List.of("basemetals:ore/copper") : null);

		JsonObject migratedOres = migrated.getAsJsonObject("ores");
		assertEquals(1, migratedOres.size());
		assertTrue(migratedOres.has("basemetals:ore/copper"));
		JsonObject migratedCopper = migratedOres.getAsJsonObject("basemetals:ore/copper");
		assertFalse(migratedCopper.get("enabled").getAsBoolean());
		assertEquals(7.5D, rule(migratedCopper).get("frequency").getAsDouble());
		assertEquals(10, rule(migratedCopper).get("min_quantity").getAsInt());
		assertEquals(13, rule(migratedCopper).get("max_quantity").getAsInt());
		String report = Files.readString(temporary.resolve("orespawn-migration/migration-report.txt"));
		assertTrue(report.contains("Mapped legacy rule copper_ore to provider rule basemetals:ore/copper"));
	}

	@Test
	void retainsLegacyIdsAndWarnsForAmbiguousAndUnmatchedProviderOutputs() throws IOException {
		Path legacyDirectory = Files.createDirectories(temporary.resolve("orespawn3"));
		JsonObject root = new JsonObject();
		root.addProperty("version", "2.0");
		JsonObject spawns = new JsonObject();
		spawns.add("copper_ore", spawn("copper_ore", null, 8, 4, 1, 0, 64));
		spawns.add("tin_ore", spawn("tin_ore", null, 8, 4, 1, 0, 64));
		root.add("spawns", spawns);
		Files.writeString(legacyDirectory.resolve("basemetals.json"), root.toString(), StandardCharsets.UTF_8);
		JsonObject defaults = new JsonObject();
		defaults.add("ores", new JsonObject());

		JsonObject migrated = LegacyConfigMigrator.migrateIfNeeded(
				temporary.resolve("orespawn-worldgen.json"), defaults,
				(owner, output) -> output.endsWith("copper_ore")
						? List.of("basemetals:ore/copper", "basemetals:ore/alternate_copper") : List.of());

		JsonObject migratedOres = migrated.getAsJsonObject("ores");
		assertTrue(migratedOres.has("orespawn:legacy/basemetals/copper_ore"));
		assertTrue(migratedOres.has("orespawn:legacy/basemetals/tin_ore"));
		String report = Files.readString(temporary.resolve("orespawn-migration/migration-report.txt"));
		assertTrue(report.contains("ambiguous ore rules"));
		assertTrue(report.contains("no ore rule matching legacy output"));
	}

	private static JsonObject baseMetalsFixture() {
		JsonObject root = new JsonObject();
		root.addProperty("version", "2.0");
		JsonObject spawns = new JsonObject();
		spawns.add("coldiron_ore", spawn("coldiron_ore", -1, 8, 4, 5.0D, 0, 128));
		spawns.add("adamantine_ore", spawn("adamantine_ore", -1, 8, 4, 2.0D, 0, 128));
		spawns.add("starsteel_ore", spawn("starsteel_ore", 1, 8, 4, 5.0D, 0, 255));
		spawns.add("copper_ore", spawn("copper_ore", null, 8, 4, 10.0D, 0, 96));
		spawns.add("silver_ore", spawn("silver_ore", null, 8, 4, 4.0D, 0, 32));
		spawns.add("tin_ore", spawn("tin_ore", null, 8, 4, 10.0D, 0, 128));
		spawns.add("lead_ore", spawn("lead_ore", null, 8, 4, 5.0D, 0, 64));
		spawns.add("zinc_ore", spawn("zinc_ore", null, 8, 4, 5.0D, 0, 96));
		spawns.add("mercury_ore", spawn("mercury_ore", null, 8, 4, 3.0D, 0, 32));
		spawns.add("nickel_ore", spawn("nickel_ore", null, 8, 4, 1.0D, 32, 96));
		spawns.add("platinum_ore", spawn("platinum_ore", null, 8, 4, 0.125D, 1, 32));
		root.add("spawns", spawns);
		return root;
	}

	private static JsonObject spawn(String name, Integer dimension, int size, int variation,
			double frequency, int minY, Integer maxY) {
		JsonObject spawn = new JsonObject();
		spawn.addProperty("enabled", true);
		spawn.addProperty("feature", "default");
		spawn.addProperty("replaces", "default");
		JsonArray dimensions = new JsonArray();
		if (dimension != null) dimensions.add(dimension);
		spawn.add("dimensions", dimensions);
		JsonObject parameters = new JsonObject();
		parameters.addProperty("size", size);
		parameters.addProperty("variation", variation);
		parameters.addProperty("frequency", frequency);
		parameters.addProperty("minHeight", minY);
		if (maxY != null) parameters.addProperty("maxHeight", maxY);
		spawn.add("parameters", parameters);
		JsonObject output = new JsonObject();
		output.addProperty("name", "basemetals:" + name);
		output.addProperty("chance", 100);
		JsonArray blocks = new JsonArray();
		blocks.add(output);
		spawn.add("blocks", blocks);
		return spawn;
	}

	private static JsonObject ore(JsonObject ores, String name) {
		return ore(ores, "basemetals", name);
	}

	private static JsonObject ore(JsonObject ores, String owner, String name) {
		return ores.getAsJsonObject("orespawn:legacy/" + owner + "/" + name);
	}

	private static JsonObject rule(JsonObject ore) {
		if (ore.has("dimension_selectors")) {
			return ore.getAsJsonObject("dimension_selectors")
					.getAsJsonObject("orespawn:all_except_nether_end");
		}
		return ore.getAsJsonObject("dimensions").entrySet().iterator().next()
				.getValue().getAsJsonObject();
	}
}
