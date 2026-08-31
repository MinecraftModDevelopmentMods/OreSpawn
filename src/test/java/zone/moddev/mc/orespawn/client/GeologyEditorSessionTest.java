package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

class GeologyEditorSessionTest {
	@Test
	void emptyStandaloneProfileIsValidAndFirstRockActivatesOverworldTerrain() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		assertFalse(session.hasTerrainRules());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());

		session.assignRock("minecraft:stone", zone.moddev.mc.orespawn.worldgen.RockFamily.SEDIMENTARY);
		assertTrue(session.hasTerrainRules());
		JsonObject overworld = session.section("terrain_dimensions")
				.getAsJsonObject("minecraft:overworld");
		assertTrue(overworld.getAsJsonArray("host_blocks").toString().contains("minecraft:stone"));
		assertTrue(overworld.getAsJsonArray("host_blocks").toString().contains("minecraft:deepslate"));
	}

	@Test
	void namespacedGeomesCanBeAddedValidatedAndRoundTripped() {
		String geomeId = "cakeworld:cocoa_basin";
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.configureDefaultVanillaStrata();
		session.addGeome(geomeId);

		assertTrue(session.section("geomes").has(geomeId));
		session.weightMap("biomes", "minecraft:plains").addProperty(geomeId, 2.0D);
		session.rock("minecraft:stone").getAsJsonObject("geomes").addProperty(geomeId, 3.0D);
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());

		WorldGeologyProfile saved = session.profile();
		GeologyEditorSession reopened = new GeologyEditorSession(saved);
		assertEquals(saved.rootCopy(), reopened.profile().rootCopy());
		assertTrue(reopened.validate().isEmpty(), reopened.validate().toString());
		assertEquals(2.0D, reopened.weightMap("biomes", "minecraft:plains")
				.get(geomeId).getAsDouble());
		assertEquals(3.0D, reopened.rock("minecraft:stone").getAsJsonObject("geomes")
				.get(geomeId).getAsDouble());
	}

	@Test
	void firstUseStrataStartsWithBalancedVanillaRocks() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));

		assertTrue(session.configureDefaultVanillaStrata());
		assertTrue(session.hasTerrainRules());
		assertEquals(6, session.section("rocks").size());
		assertFalse(session.section("rocks").has("minecraft:calcite"));
		assertFalse(session.section("rocks").has("minecraft:dripstone_block"));
		assertEquals("sedimentary", session.rock("minecraft:stone").get("family").getAsString());
		assertEquals("metamorphic", session.rock("minecraft:deepslate").get("family").getAsString());
		assertEquals("igneous_intrusive", session.rock("minecraft:granite").get("family").getAsString());
		assertEquals("igneous_volcanic", session.rock("minecraft:tuff").get("family").getAsString());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void firstUseStrataDoesNotOverwriteAnExistingRockChoice() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.assignRock("minecraft:coal_block",
				zone.moddev.mc.orespawn.worldgen.RockFamily.SEDIMENTARY);

		assertFalse(session.configureDefaultVanillaStrata());
		assertEquals(1, session.section("rocks").size());
		assertTrue(session.section("rocks").has("minecraft:coal_block"));
	}

	@Test
	void availableDimensionsPutVanillaFirstAndIncludeInstalledAndConfiguredIds() {
		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfile.recommended(false),
				Arrays.asList("zeta:moon", "alpha:void", "not a valid id"));

		JsonObject dimensions = new JsonObject();
		dimensions.add("example:caverns", new JsonObject());
		session.ore("example:test_ore").add("dimensions", dimensions);

		assertEquals(Arrays.asList(
				"minecraft:overworld",
				"minecraft:the_nether",
				"minecraft:the_end",
				"alpha:void",
				"example:caverns",
				"zeta:moon"), session.availableDimensionIds());
	}

	@Test
	void providerFluidRemovalLeavesATombstoneButLocalRulesAreDeleted() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		JsonObject providerRule = new JsonObject();
		providerRule.addProperty("source_provider", "examplemod");
		providerRule.addProperty("enabled", true);
		session.section("fluid_deposits").add("examplemod:fluid_deposit/brine", providerRule);
		JsonObject localRule = new JsonObject();
		session.section("fluid_deposits").add("pack:fluid_deposit/test", localRule);

		session.removeFluidDeposit("examplemod:fluid_deposit/brine");
		session.removeFluidDeposit("pack:fluid_deposit/test");

		JsonObject tombstone = session.section("fluid_deposits")
				.getAsJsonObject("examplemod:fluid_deposit/brine");
		assertFalse(tombstone.get("enabled").getAsBoolean());
		assertTrue(tombstone.get("unassigned").getAsBoolean());
		assertFalse(session.section("fluid_deposits").has("pack:fluid_deposit/test"));
	}

	@Test
	void rangedSelectorOreIsValidWithoutAnExplicitDimension() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		JsonObject ore = session.ore("minecraft:coal_ore");
		ore.addProperty("block", "minecraft:coal_ore");
		ore.addProperty("enabled", true);
		JsonObject rule = GeologyEditorSession.defaultOreDimension();
		rule.remove("quantity");
		rule.addProperty("min_quantity", 4);
		rule.addProperty("max_quantity", 11);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:stone_ore_replaceables");
		rule.add("host_tags", tags);
		JsonObject selectors = new JsonObject();
		selectors.add("orespawn:all_except_nether_end", rule);
		ore.add("dimension_selectors", selectors);

		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void standaloneFluidPickerCreatesAUsableCoveredOverworldRule() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.configureDefaultVanillaStrata();

		String id = session.assignFluidDeposit("minecraft:water");

		assertEquals("orespawn:fluid_deposit/minecraft/water", id);
		JsonObject deposit = session.fluidDeposit(id);
		assertEquals("minecraft:water", deposit.get("block").getAsString());
		JsonObject overworld = deposit.getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld");
		assertEquals(2, overworld.get("min_solid_cover").getAsInt());
		assertEquals(1, overworld.get("min_solid_shell").getAsInt());
		assertEquals(4, overworld.getAsJsonArray("host_families").size());
		assertFalse(session.availableFluidBlockIds("").contains("minecraft:water"));
		assertEquals(id, session.assignFluidDeposit("minecraft:water"));
		assertEquals(1, session.section("fluid_deposits").size());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}
}
