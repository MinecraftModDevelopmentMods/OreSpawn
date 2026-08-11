package com.mcmoddev.orespawn.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IBiomeBuilder;
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;

import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class LegacyOs3BridgeTest {
	@TempDir Path temporary;

	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void translatesOs1MetadataFractionalFrequencyExclusiveHeightAndPlusDimensions() throws Exception {
		JsonObject source = new JsonParser().parse("{\"dimensions\":["
				+ "{\"dimension\":-1,\"ores\":[{\"blockID\":\"minecraft:quartz_ore\",\"size\":4,\"frequency\":2,\"minHeight\":8,\"maxHeight\":120}]},"
				+ "{\"dimension\":\"+\",\"ores\":[{\"blockID\":\"minecraft:stone\",\"blockMeta\":3,\"size\":9,\"variation\":2,\"frequency\":0.25,\"minHeight\":7,\"maxHeight\":64,\"biomes\":[\"Plains\"]}]}]}"
				).getAsJsonObject();
		JsonObject intermediate = LegacyOs3Bridge.translateOs1ForTests("minecraft", source);
		JsonObject plus = null;
		for (Map.Entry<String, com.google.gson.JsonElement> entry : intermediate.getAsJsonObject("spawns").entrySet()) {
			JsonObject candidate = entry.getValue().getAsJsonObject();
			if (candidate.getAsJsonArray("blocks").get(0).getAsJsonObject().get("name").getAsString().equals("minecraft:stone")) plus = candidate;
		}
		assertNotNull(plus);
		assertEquals(3, plus.getAsJsonArray("blocks").get(0).getAsJsonObject().get("metadata").getAsInt());
		assertEquals(0.25D, plus.getAsJsonObject("parameters").get("frequency").getAsDouble());
		assertFalse(plus.getAsJsonArray("dimensions").toString().contains("-1"));

		Path config = temporary.resolve("orespawn.cfg");
		Files.write(config, new byte[0]);
		JsonObject provider = LegacyOs3Bridge.translateForTests("minecraft", intermediate,
				temporary.resolve("orespawn3"), config);
		JsonObject placement = null;
		for (Map.Entry<String, com.google.gson.JsonElement> ore : provider.getAsJsonObject("ores").entrySet()) {
			JsonObject value = ore.getValue().getAsJsonObject();
			if (value.get("block").getAsString().equals("minecraft:stone")) {
				placement = value.getAsJsonObject("dimensions").entrySet().iterator().next().getValue().getAsJsonObject();
			}
		}
		assertNotNull(placement);
		assertEquals(63, placement.get("max_y").getAsInt(), "OS1 maxHeight is exclusive");
		assertEquals(0.25D, placement.get("frequency").getAsDouble());
		assertEquals("minecraft:plains", placement.getAsJsonArray("biome_ids").get(0).getAsString());
	}

	@Test
	void unresolvedOs1BiomeRestrictionNeverBroadensToAllBiomes() {
		JsonObject source = new JsonParser().parse("{\"dimensions\":[{\"dimension\":0,\"ores\":["
				+ "{\"blockID\":\"minecraft:iron_ore\",\"biomes\":[\"Definitely Missing\"]}]}]}").getAsJsonObject();
		JsonObject converted = LegacyOs3Bridge.translateOs1ForTests("minecraft", source);
		JsonObject spawn = converted.getAsJsonObject("spawns").entrySet().iterator().next().getValue().getAsJsonObject();
		assertEquals("orespawn:unresolved_legacy_biome",
				spawn.getAsJsonObject("biomes").getAsJsonArray("includes").get(0).getAsString());
	}

	@Test
	void translatesAllOs3PatternsSelectorsWeightsMetadataAndFlags() throws Exception {
		Path legacy = temporary.resolve("orespawn3");
		Files.createDirectories(legacy.resolve("sysconf"));
		Files.write(legacy.resolve("sysconf/replacements-test.json"), (
				"[{\"name\":\"granite_only\",\"blockName\":\"minecraft:stone\","
				+ "\"blockState\":\"variant=granite\"}]").getBytes(StandardCharsets.UTF_8));
		Path config = temporary.resolve("orespawn.cfg");
		Files.write(config, ("general {\n B:\"Replace Vanilla Oregen\"=true\n B:Retrogen=true\n}\n"
				+ "options {\n B:disable_standard_ore_generation=true\n"
				+ " S:nonstandard_spawn_blocks=minecraft:stone@3\n}\n").getBytes(StandardCharsets.UTF_8));

		JsonObject source = new JsonObject(); JsonObject spawns = new JsonObject(); source.add("spawns", spawns);
		String[] patterns = { "default", "vein", "normal-cloud", "precision", "clusters", "underfluids" };
		for (int index = 0; index < patterns.length; index++) {
			JsonObject spawn = new JsonObject(); spawn.addProperty("enabled", index != 4);
			spawn.addProperty("retrogen", index == 1); spawn.addProperty("feature", patterns[index]);
			spawn.addProperty("replaces", index == 0 ? "granite_only" : "default");
			JsonArray dimensions = new JsonArray(); dimensions.add(new JsonPrimitive(index == 2 ? -1 : index == 3 ? 1 : 0));
			spawn.add("dimensions", dimensions);
			JsonObject biomes = new JsonObject(); JsonArray includes = new JsonArray();
			includes.add(new JsonPrimitive(index == 0 ? "minecraft:plains" : "FOREST")); biomes.add("includes", includes); spawn.add("biomes", biomes);
			JsonObject parameters = new JsonObject(); parameters.addProperty("frequency", index == 0 ? 0.375D : 50.0D);
			parameters.addProperty("size", 9); parameters.addProperty("variation", 2);
			parameters.addProperty("attemptsMin", 2); parameters.addProperty("attemptsMax", 2);
			if (index == 3) parameters.addProperty("numObjects", 2);
			parameters.addProperty("minHeight", 7); parameters.addProperty("maxHeight", 41);
			spawn.add("parameters", parameters);
			JsonArray blocks = new JsonArray(); JsonObject output = new JsonObject(); output.addProperty("name", "minecraft:stone");
			output.addProperty("state", "variant=andesite"); output.addProperty("chance", 70); blocks.add(output);
			JsonObject second = new JsonObject(); second.addProperty("name", "minecraft:iron_ore"); second.addProperty("chance", 30); blocks.add(second);
			spawn.add("blocks", blocks); spawns.add("pattern_" + index, spawn);
		}

		JsonObject provider = LegacyOs3Bridge.translateForTests("orespawn", source, legacy, config);
		assertEquals(6, provider.getAsJsonObject("ores").entrySet().size());
		for (int index = 0; index < patterns.length; index++) {
			JsonObject ore = provider.getAsJsonObject("ores").getAsJsonObject("orespawn:legacy/pattern_" + index);
			assertEquals(index == 4, !ore.get("enabled").getAsBoolean());
			assertEquals(index == 1, ore.get("retrogen").getAsBoolean());
			assertTrue(ore.get("suppress_vanilla").getAsBoolean());
			assertEquals(5, ore.get("metadata").getAsInt());
			JsonObject placement = ore.getAsJsonObject("dimensions").entrySet().iterator().next().getValue().getAsJsonObject();
			assertEquals("orespawn:" + patterns[index].replace('-', '_'), placement.get("pattern").getAsString());
			assertEquals(index == 0 ? 0.375D : index == 3 || index == 5 ? 2.0D : 1.0D,
					placement.get("frequency").getAsDouble());
			assertEquals(40, placement.get("max_y").getAsInt());
			assertEquals(70, ore.getAsJsonArray("outputs").get(0).getAsJsonObject().get("weight").getAsInt());
		}
		JsonObject defaultPlacement = provider.getAsJsonObject("ores").getAsJsonObject("orespawn:legacy/pattern_0")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals(7, defaultPlacement.get("min_quantity").getAsInt());
		assertEquals(10, defaultPlacement.get("max_quantity").getAsInt());
		assertFalse(defaultPlacement.has("quantity"));
		JsonObject firstPlacement = provider.getAsJsonObject("ores").getAsJsonObject("orespawn:legacy/pattern_0")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals(1, firstPlacement.getAsJsonArray("host_blocks").get(0).getAsJsonObject().get("metadata").getAsInt());
		assertEquals(1, firstPlacement.getAsJsonArray("host_blocks").size());
		assertEquals("minecraft:plains", firstPlacement.getAsJsonArray("biome_ids").get(0).getAsString());
		JsonObject dictionaryPlacement = provider.getAsJsonObject("ores").getAsJsonObject("orespawn:legacy/pattern_1")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals("FOREST", dictionaryPlacement.getAsJsonArray("biome_dictionary").get(0).getAsString());
	}

	@Test
	void translatesStandaloneConfigThroughOreSpawnOwnershipAndDefaultDimensionSelector() throws Exception {
		JsonObject source = new JsonObject();
		JsonObject spawns = new JsonObject();
		JsonObject spawn = new JsonObject();
		spawn.addProperty("enabled", true);
		spawn.addProperty("feature", "default");
		spawn.addProperty("replaces", "default");
		spawn.add("dimensions", new JsonArray());
		spawn.add("parameters", new JsonObject());
		JsonArray blocks = new JsonArray();
		JsonObject output = new JsonObject(); output.addProperty("name", "minecraft:iron_ore");
		blocks.add(output); spawn.add("blocks", blocks); spawns.add("matrix", spawn); source.add("spawns", spawns);

		JsonObject provider = LegacyOs3Bridge.translateStandaloneForTests(
				"custom-migration-matrix", source, temporary.resolve("missing"), temporary.resolve("missing.cfg"));
		assertEquals("orespawn", provider.get("provider_modid").getAsString());
		JsonObject ore = provider.getAsJsonObject("ores")
				.getAsJsonObject("orespawn:legacy/custom-migration-matrix/matrix");
		assertEquals("custom-migration-matrix", ore.get("source_mod").getAsString());
		assertFalse(ore.has("dimensions"));
		JsonObject placement = ore.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		assertEquals(255, placement.get("max_y").getAsInt());
		assertEquals(3, placement.getAsJsonArray("host_blocks").size());
	}

	@Test
	void malformedSpawnsAreReportedWithoutInventingRules() throws Exception {
		JsonObject source = new JsonParser().parse("{\"spawns\":{\"broken\":{\"blocks\":[]}}}").getAsJsonObject();
		JsonObject provider = LegacyOs3Bridge.translateForTests("orespawn", source,
				temporary.resolve("missing"), temporary.resolve("missing.cfg"));
		assertTrue(provider.getAsJsonObject("ores").entrySet().isEmpty());
		assertFalse(Files.exists(temporary.resolve("missing.cfg")));
	}

	@Test
	void translates331ProgrammaticBuildersWithMetadataWeightsSelectorsAndDefaults() throws Exception {
		LegacyOs3Bridge.resetProgrammaticForTests("fixture331");
		OS3API api = LegacyOs3Bridge.api();
		IReplacementEntry replacement = api.getReplacementBuilder().setName("fixture331:granite_host")
				.addEntry(Blocks.STONE.getStateFromMeta(1)).create();
		IDimensionBuilder dimensions = api.getDimensionBuilder();
		IBiomeBuilder biomes = api.getBiomeBuilder();
		IFeatureEntry feature = api.getFeatureBuilder().setFeature("default")
				.setParameter("frequency", 0.375F).setParameter("size", 9).create();
		ISpawnEntry spawn = api.getSpawnBuilder().setName("weighted_metadata")
				.setDimensions(dimensions.create())
				.setBiomes(biomes.addBlacklistEntry("minecraft:desert").create())
				.setEnabled(true).setRetrogen(true).setReplacement(replacement).setFeature(feature)
				.addBlockWithChance("minecraft:stone", 5, 70)
				.addBlockWithChance("minecraft:iron_ore", 30).create();
		api.addSpawn(spawn);

		Map<String, JsonObject> sources = LegacyOs3Bridge.programmaticSourcesForTests();
		JsonObject rule = sources.get("fixture331").getAsJsonObject("spawns")
				.getAsJsonObject("weighted_metadata");
		assertNotNull(rule);
		assertEquals(0, rule.getAsJsonArray("dimensions").size());
		assertEquals("minecraft:desert", rule.getAsJsonObject("biomes")
				.getAsJsonArray("excludes").get(0).getAsString());
		assertEquals(5, rule.getAsJsonArray("blocks").get(0).getAsJsonObject().get("metadata").getAsInt());
		assertEquals(70, rule.getAsJsonArray("blocks").get(0).getAsJsonObject().get("chance").getAsInt());
		assertEquals(0.375F, rule.getAsJsonObject("parameters").get("frequency").getAsFloat());
		assertEquals(1, LegacyOs3Bridge.translatedProgrammaticCountsForTests()[0]);
	}

	@Test
	void translates322BuiltInBuildersButKeepsCustomFeaturesOnOneScheduler() throws Exception {
		LegacyOs3Bridge.resetProgrammaticForTests("fixture322");
		OS3API api = LegacyOs3Bridge.api();
		BuilderLogic logic = api.getLogic("programmatic_322");
		DimensionBuilder dimension = logic.newDimensionBuilder("+");
		SpawnBuilder spawn = dimension.newSpawnBuilder("dictionary_weighted");
		BiomeBuilder biomes = spawn.newBiomeBuilder().whitelistBiomeByDictionary("forest")
				.blacklistBiomeByName("minecraft:roofed_forest");
		FeatureBuilder feature = spawn.newFeatureBuilder("vein")
				.addParameter("frequency", 2.0F).addParameter("size", 7)
				.addParameter("length", 11).addParameter("minHeight", 5)
				.addParameter("maxHeight", 48);
		OreBuilder first = spawn.newOreBuilder().setOre("minecraft:stone", 3, 65);
		OreBuilder second = spawn.newOreBuilder().setOre("minecraft:gold_ore", 35);
		spawn.create(biomes, feature, java.util.Collections.singletonList(Blocks.STONE.getStateFromMeta(5)),
				first, second).enabled(true);
		dimension.create(spawn);
		logic.create(dimension);
		api.registerLogic(logic);

		Map<String, JsonObject> sources = LegacyOs3Bridge.programmaticSourcesForTests();
		JsonObject rule = sources.get("fixture322").getAsJsonObject("spawns")
				.getAsJsonObject("dictionary_weighted");
		assertNotNull(rule);
		assertEquals(0, rule.getAsJsonArray("dimensions").size());
		assertEquals("FOREST", rule.getAsJsonObject("biomes").getAsJsonArray("includes").get(0).getAsString());
		assertEquals(3, rule.getAsJsonArray("blocks").get(0).getAsJsonObject().get("metadata").getAsInt());
		assertEquals(65, rule.getAsJsonArray("blocks").get(0).getAsJsonObject().get("chance").getAsInt());
		assertEquals(1, LegacyOs3Bridge.translatedProgrammaticCountsForTests()[1]);

		LegacyOs3Bridge.resetProgrammaticForTests("fixturecustom");
		api = LegacyOs3Bridge.api();
		api.registerFeatureGenerator("fixturecustom:custom", new IFeature() {
			@Override public void setRandom(Random random) { }
			@Override public JsonObject getDefaultParameters() { return new JsonObject(); }
		});
		ISpawnEntry custom = api.getSpawnBuilder().setName("custom_generator")
				.setDimensions(api.getDimensionBuilder().create())
				.setBiomes(api.getBiomeBuilder().setAcceptAll().create()).setEnabled(true)
				.setReplacement(api.getReplacementBuilder().setName("fixturecustom:stone")
						.addEntry(Blocks.STONE.getDefaultState()).create())
				.setFeature(api.getFeatureBuilder().setFeature("fixturecustom:custom").create())
				.addBlock(Blocks.IRON_ORE).create();
		api.addSpawn(custom);
		sources = LegacyOs3Bridge.programmaticSourcesForTests();
		assertFalse(sources.containsKey("fixturecustom"));
		assertEquals(0, LegacyOs3Bridge.translatedProgrammaticCountsForTests()[0]);
		assertTrue(LegacyOs3Bridge.reportForTests().stream()
				.anyMatch(row -> row.equals("programmatic_custom_scheduled=custom_generator")));
	}
}
