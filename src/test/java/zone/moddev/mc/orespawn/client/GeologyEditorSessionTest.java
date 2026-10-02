package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.block.Blocks;
import zone.moddev.mc.orespawn.util.FluidBlocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

import net.minecraft.util.registry.Bootstrap;

class GeologyEditorSessionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Bootstrap.register();
	}

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
		assertFalse(overworld.getAsJsonArray("host_blocks").toString().contains("minecraft:deepslate"));
	}

	@Test
	void firstUseStrataStartsWithBalancedVanillaRocks() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));

		assertTrue(session.configureDefaultVanillaStrata());
		assertTrue(session.hasTerrainRules());
		assertEquals(4, session.section("rocks").size());
		assertFalse(session.section("rocks").has("minecraft:calcite"));
		assertFalse(session.section("rocks").has("minecraft:dripstone_block"));
		assertEquals("sedimentary", session.rock("minecraft:stone").get("family").getAsString());
		assertEquals("igneous_intrusive", session.rock("minecraft:granite").get("family").getAsString());
		assertEquals("igneous_volcanic", session.rock("minecraft:andesite").get("family").getAsString());
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
		tags.add("forge:stone");
		rule.add("host_tags", tags);
		JsonObject selectors = new JsonObject();
		selectors.add("orespawn:all_except_nether_end", rule);
		ore.add("dimension_selectors", selectors);

		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void fluidPickerExcludesWaterloggedBlocksAndBubbleColumns() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		assertTrue(FluidBlocks.isFluidBlock(Blocks.WATER));
		assertTrue(FluidBlocks.isFluidBlock(Blocks.LAVA));
		assertFalse(FluidBlocks.isFluidBlock(Blocks.BRAIN_CORAL_WALL_FAN));
		assertFalse(FluidBlocks.isFluidBlock(Blocks.BUBBLE_COLUMN));
		assertFalse(session.availableFluidBlockIds("").contains("minecraft:brain_coral_wall_fan"));
		assertFalse(session.availableFluidBlockIds("").contains("minecraft:bubble_column"));
		assertFalse(session.availableMaterialBlockIds("", true).contains("minecraft:brain_coral_wall_fan"));
		assertNull(session.assignFluidDeposit("minecraft:brain_coral_wall_fan"));
		session.setMaterialBlock("minecraft:overworld", "default_fluid",
				"minecraft:brain_coral_wall_fan", true);
		assertNull(session.dimensionMaterials("minecraft:overworld", false));
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
	void invalidGeomeIdsAreRejectedWithoutRenamingThem() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.configureDefaultVanillaStrata();
		session.addGeome("");
		session.addGeome("BAD:UPPER");
		session.addGeome("cakeworld:cocoa_basin");
		JsonObject beforeDuplicate = session.profile().rootCopy();
		session.addGeome("cakeworld:cocoa_basin");
		assertFalse(session.section("geomes").has(""));
		assertFalse(session.section("geomes").has("bad:upper"));
		assertTrue(session.section("geomes").has("cakeworld:cocoa_basin"));
		assertEquals(beforeDuplicate, session.profile().rootCopy());
	}

	@Test
	void balancedModeRestoresEveryEligibleOutputAfterCustomSelection() {
		JsonObject root = WorldGeologyProfile.recommended(false).rootCopy();
		JsonObject policy = new JsonObject();
		policy.addProperty("material", "orespawn:sulfur");
		policy.addProperty("domain", "minecraft:overworld");
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("output_mode", "custom");
		JsonObject outputs = new JsonObject();
		outputs.addProperty("mineralogy:sulfur", 1.0D);
		policy.add("outputs", outputs);
		JsonObject placements = new JsonObject();
		placements.addProperty("orespawn:standard", "mineralogy:sulfur");
		policy.add("placement_sources", placements);
		JsonArray candidates = new JsonArray();
		candidates.add(oreSourceCandidate("mineralogy:sulfur", "mineralogy:sulfur_ore", false));
		candidates.add(oreSourceCandidate("baseminerals:sulfur", "baseminerals:sulfur_ore", false));
		candidates.add(oreSourceCandidate("outside:sulfur", "outside:sulfur_ore", true));
		policy.add("candidates", candidates);
		JsonObject policies = new JsonObject();
		String key = "orespawn:sulfur|minecraft:overworld";
		policies.add(key, policy);
		root.add("ore_source_policies", policies);
		WorldGeologyProfile original = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		JsonObject before = original.rootCopy();
		GeologyEditorSession session = new GeologyEditorSession(original);

		session.setOreSourceOutputMode(key, "balanced");
		GeologyEditorSession.OreSourceGroup balanced = session.oreSourceGroups().get(0);
		assertEquals("consolidated", balanced.mode);
		assertEquals(2, balanced.outputs.size());
		assertTrue(balanced.outputs.containsKey("mineralogy:sulfur"));
		assertTrue(balanced.outputs.containsKey("baseminerals:sulfur"));
		assertFalse(balanced.outputs.containsKey("outside:sulfur"));

		session.setOreSourceOutputMode(key, "custom");
		session.setOreSourceOutput(key, "baseminerals:sulfur", true, 2.5D);
		session.setOreSourceOutput(key, "mineralogy:sulfur", false, 1.0D);
		assertEquals(1, session.oreSourceGroups().get(0).outputs.size());
		session.setOreSourceOutputMode(key, "balanced");
		assertEquals(2, session.oreSourceGroups().get(0).outputs.size());
		assertEquals(1.0D, session.oreSourceGroups().get(0).outputs
				.get("mineralogy:sulfur").doubleValue());
		assertEquals(before, original.rootCopy(), "the main editor still owns persistence");
	}

	private static JsonObject oreSourceCandidate(String source, String block, boolean external) {
		JsonObject candidate = new JsonObject();
		candidate.addProperty("source_id", source);
		candidate.addProperty("owner", source.substring(0, source.indexOf(':')));
		candidate.addProperty("registry_id", block);
		candidate.addProperty("metadata", 0);
		candidate.addProperty("material", "orespawn:sulfur");
		candidate.addProperty("domain", "minecraft:overworld");
		candidate.addProperty("placement_channel", "orespawn:standard");
		JsonArray tags = new JsonArray();
		tags.add("forge:ores/sulfur");
		candidate.add("block_tags", tags);
		candidate.addProperty("loaded", true);
		candidate.addProperty("placement_active", !external);
		candidate.addProperty("external", external);
		return candidate;
	}
}
