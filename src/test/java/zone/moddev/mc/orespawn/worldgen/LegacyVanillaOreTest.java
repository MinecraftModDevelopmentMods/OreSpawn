package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.MinecraftTestBootstrap;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnPatternRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

class LegacyVanillaOreTest {

	@BeforeAll
	@SuppressWarnings("unchecked")
	static void bootstrap() throws Exception {
		MinecraftTestBootstrap.registerVanilla();
		var registry = OreSpawnPatterns.registry();
		if (registry.size() == 0) {
			// Unit tests do not run the mod-loading bus, so register the real factories here.
			Field types = OreSpawnPatterns.class.getDeclaredField("TYPES");
			types.setAccessible(true);
			Field entries = DeferredRegister.class.getDeclaredField("entries");
			entries.setAccessible(true);
			var patterns = (Map<DeferredHolder<OrePatternType, ? extends OrePatternType>,
					java.util.function.Supplier<? extends OrePatternType>>) entries.get(types.get(null));
			patterns.forEach((id, factory) -> Registry.register(registry, id.getId(), factory.get()));
		}
	}

	@Test
	void importedDiamondStaysInactiveWhileVanillaManagementIsOff() throws Exception {
		JsonObject profile = profile(false);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		String original = profile.toString();

		Object baked = bake(profile);

		assertEquals(0, ores(baked, Level.OVERWORLD).length);
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.IRON_ORE));
		assertFalse(takeover(baked, Level.NETHER, Blocks.DIAMOND_ORE));
		assertEquals(original, profile.toString(), "baking must not rewrite a saved profile");
	}

	@Test
	void explicitImportedRulesAlsoRespectTheSwitchInEveryDimension() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		JsonObject placement = imported.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		imported.remove("dimension_selectors");
		JsonObject dimensions = new JsonObject();
		for (ResourceKey<Level> dimension : java.util.List.of(
				Level.OVERWORLD, Level.NETHER, Level.END)) {
			dimensions.add(dimension.location().toString(), new com.google.gson.JsonParser().parse(placement.toString()));
		}
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);
		String original = profile.toString();

		Object baked = bake(profile);

		for (ResourceKey<Level> dimension : java.util.List.of(
				Level.OVERWORLD, Level.NETHER, Level.END)) {
			assertEquals(0, ores(baked, dimension).length);
			assertFalse(takeover(baked, dimension, Blocks.DIAMOND_ORE));
		}
		assertEquals(original, profile.toString());
	}

	@Test
	void switchingOffAndBackOnPreservesTheImportedSettings() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		String original = profile.toString();

		assertEquals(1, ores(bake(profile), Level.OVERWORLD).length);
		profile.addProperty("manage_vanilla_ores", false);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, Level.OVERWORLD).length);
		assertFalse(takeover(disabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		Object[] restored = ores(bake(profile), Level.OVERWORLD);
		assertEquals(1, restored.length);
		assertEquals(ResourceLocation.parse("orespawn:legacy/diamond_ore"), field(restored[0], "ruleId"));
		assertEquals(0.375D, field(restored[0], "frequency"));
		assertEquals(7, field(restored[0], "minY"));
		assertEquals(11, field(restored[0], "maxY"));
		assertEquals(false, field(restored[0], "retrogen"));
		assertEquals(original, profile.toString());
	}

	@Test
	void aMissingManagementSwitchDefaultsToVanillaGeneration() throws Exception {
		JsonObject profile = profile(false);
		profile.remove("manage_vanilla_ores");
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));

		Object baked = bake(profile);

		assertEquals(0, ores(baked, Level.OVERWORLD).length);
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		assertFalse(profile.has("manage_vanilla_ores"));
	}

	@Test
	void importedSettingsWinOverModernDefaultsWhenBothAreEnabled() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));

		Object[] ores = ores(bake(profile), Level.OVERWORLD);

		assertEquals(1, ores.length, "the native default must not add a second placement pass");
		assertEquals(ResourceLocation.parse("orespawn:legacy/diamond_ore"), field(ores[0], "ruleId"));
		assertEquals(0.375D, field(ores[0], "frequency"));
		assertEquals(7, field(ores[0], "minY"));
		assertEquals(11, field(ores[0], "maxY"));
		assertEquals(4, field(ores[0], "minQuantity"));
		assertEquals(4, field(ores[0], "maxQuantity"));
		assertEquals(false, field(ores[0], "retrogen"));
	}

	@Test
	void disabledImportedRuleDoesNotSuppressAnOtherwiseEnabledVanillaOre() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, false));

		Object[] ores = ores(bake(profile), Level.OVERWORLD);

		assertEquals(1, ores.length);
		assertEquals(ResourceLocation.parse("minecraft:diamond_ore"), field(ores[0], "ruleId"));
		profile.addProperty("manage_vanilla_ores", false);
		assertFalse(takeover(bake(profile), Level.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void anExplicitDimensionLimitDoesNotRemoveTheOverworldDefault() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		JsonObject imported = rule(false, true);
		JsonObject placement = imported.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		imported.remove("dimension_selectors");
		JsonObject dimensions = new JsonObject();
		dimensions.add("examplemod:moon", placement);
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);

		Object baked = bake(profile);

		assertEquals(ResourceLocation.parse("minecraft:diamond_ore"),
				field(ores(baked, Level.OVERWORLD)[0], "ruleId"));
		assertTrue(takeover(baked, ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("examplemod:moon")), Blocks.DIAMOND_ORE));
	}

	@Test
	void aDisabledDimensionOverridesAnImportedSelectorWithoutClaimingVanilla() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		JsonObject imported = rule(false, true);
		JsonObject disabled = new JsonObject();
		disabled.addProperty("enabled", false);
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", disabled);
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);

		Object baked = bake(profile);

		assertEquals(1, ores(baked, Level.OVERWORLD).length);
		assertEquals(ResourceLocation.parse("minecraft:diamond_ore"),
				field(ores(baked, Level.OVERWORLD)[0], "ruleId"));
		assertTrue(takeover(baked, ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("examplemod:moon")), Blocks.DIAMOND_ORE));
	}

	@Test
	void anExplicitOreSourceChoiceStillControlsPlacement() throws Exception {
		for (String selected : new String[] { "minecraft:diamond_ore", "orespawn:legacy/diamond_ore" }) {
			JsonObject profile = profile(true);
			JsonObject nativeRule = rule(true, true);
			nativeRule.addProperty("material", "minecraft:diamond");
			JsonObject imported = rule(false, true);
			imported.addProperty("material", "minecraft:diamond");
			JsonObject dimensions = new JsonObject();
			dimensions.add("minecraft:overworld", imported.getAsJsonObject("dimension_selectors")
					.getAsJsonObject("orespawn:all_except_nether_end"));
			imported.remove("dimension_selectors");
			imported.add("dimensions", dimensions);
			profile.getAsJsonObject("ores").add("minecraft:diamond_ore", nativeRule);
			profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);

			JsonObject policy = new JsonObject();
			policy.addProperty("mode", "consolidated");
			JsonObject outputs = new JsonObject();
			outputs.addProperty("minecraft:diamond_ore", 1.0D);
			policy.add("outputs", outputs);
			JsonObject placements = new JsonObject();
			placements.addProperty("orespawn:standard", selected);
			policy.add("placement_sources", placements);
			JsonObject policies = new JsonObject();
			policies.add(OreSourcePolicies.key(ResourceLocation.parse("minecraft:diamond"),
					Level.OVERWORLD.location()), policy);
			profile.add(OreSourcePolicies.SECTION, policies);

			Object[] ores = ores(bake(profile), Level.OVERWORLD);

			assertEquals(1, ores.length, selected);
			assertEquals(ResourceLocation.parse(selected), field(ores[0], "ruleId"));
			profile.addProperty("manage_vanilla_ores", false);
			String saved = profile.toString();
			Object disabled = bake(profile);
			assertEquals(0, ores(disabled, Level.OVERWORLD).length, selected);
			assertFalse(takeover(disabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
			assertEquals(saved, profile.toString());
		}
	}

	@Test
	void ordinaryAddOnRulesKeepTheirExistingBehaviour() throws Exception {
		JsonObject profile = profile(false);
		profile.getAsJsonObject("ores").add("examplemod:diamond_ore", rule(false, true));

		Object baked = bake(profile);

		assertEquals(1, ores(baked, Level.OVERWORLD).length);
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void providerOwnedImportsRespectTheSameManagementSwitch() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("source_mod", "basemetals");
		profile.getAsJsonObject("ores").add("basemetals:legacy/diamond_ore", imported);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, Level.OVERWORLD).length);
		assertFalse(takeover(disabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		Object enabled = bake(profile);
		assertEquals(1, ores(enabled, Level.OVERWORLD).length);
		assertTrue(takeover(enabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void importedNonVanillaBlocksDoNotRequireVanillaManagement() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("block", "minecraft:gold_block");
		profile.getAsJsonObject("ores").add("orespawn:legacy/gold_block", imported);
		Object baked = bake(profile);
		assertEquals(1, ores(baked, Level.OVERWORLD).length);
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.GOLD_BLOCK));
	}

	@Test
	void anImportMappedToAModernProviderIdStillRespectsTheSwitch() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("source_mod", "legacy");
		profile.getAsJsonObject("ores").add("basemetals:custom_diamond", imported);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, Level.OVERWORLD).length);
		assertFalse(takeover(disabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		Object enabled = bake(profile);
		Object[] ores = ores(enabled, Level.OVERWORLD);
		assertEquals(1, ores.length);
		assertEquals(ResourceLocation.parse("basemetals:custom_diamond"), field(ores[0], "ruleId"));
		assertTrue(takeover(enabled, Level.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void oreSourcesShowsImportedVanillaPlacementsAsInactiveWhileOff() throws Exception {
		for (String id : new String[] { "orespawn:legacy/diamond_ore", "basemetals:legacy/diamond_ore" }) {
			JsonObject profile = profile(false);
			JsonObject imported = rule(false, true);
			imported.addProperty("source_mod", "basemetals");
			imported.addProperty("material", "minecraft:diamond");
			profile.getAsJsonObject("ores").add(id, imported);
			OreSourcePolicies.initialize(profile, true);
			OreSourcePolicies.Candidate candidate = OreSourcePolicies.snapshot(profile).groups().get(0).candidates.get(0);
			assertEquals(id, candidate.sourceId);
			assertTrue(candidate.loaded);
			assertFalse(candidate.active);
			profile.addProperty("manage_vanilla_ores", true);
			OreSourcePolicies.initialize(profile, true);
			assertTrue(OreSourcePolicies.snapshot(profile).groups().get(0).candidates.get(0).active);
		}
	}

	@Test
	void allImportedVanillaFamiliesRespectOffAndOn() throws Exception {
		Block[] blocks = { Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
				Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.COPPER_ORE,
				Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
				Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
				Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
				Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_GOLD_ORE, Blocks.ANCIENT_DEBRIS };
		for (Block block : blocks) {
			JsonObject profile = profile(false);
			JsonObject imported = rule(false, true);
			imported.addProperty("block", BuiltInRegistries.BLOCK.getKey(block).toString());
			profile.getAsJsonObject("ores").add("orespawn:legacy/" + BuiltInRegistries.BLOCK.getKey(block).getPath(), imported);
			Object disabled = bake(profile);
			assertEquals(0, ores(disabled, Level.OVERWORLD).length, BuiltInRegistries.BLOCK.getKey(block).toString());
			assertFalse(takeover(disabled, Level.OVERWORLD, block), BuiltInRegistries.BLOCK.getKey(block).toString());
			profile.addProperty("manage_vanilla_ores", true);
			Object enabled = bake(profile);
			assertEquals(1, ores(enabled, Level.OVERWORLD).length, BuiltInRegistries.BLOCK.getKey(block).toString());
			assertTrue(takeover(enabled, Level.OVERWORLD, block), BuiltInRegistries.BLOCK.getKey(block).toString());
		}
	}

	@Test
	void invalidImportedRulesDoNotSuppressVanillaWhenManagementIsOn() throws Exception {
		for (boolean disabledSelector : new boolean[] { false, true }) {
			JsonObject profile = profile(true);
			JsonObject imported = rule(false, true);
			JsonObject placement = imported.getAsJsonObject("dimension_selectors")
					.getAsJsonObject("orespawn:all_except_nether_end");
			if (disabledSelector) placement.addProperty("enabled", false);
			else placement.addProperty("frequency", 0.0D);
			profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);
			Object baked = bake(profile);
			assertEquals(0, ores(baked, Level.OVERWORLD).length);
			assertFalse(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		}
	}

	@Test
	void importedNetherQuartzDoesNotTakeOverOtherDimensions() throws Exception {
		JsonObject profile = profile(true);
		JsonObject imported = rule(false, true);
		JsonObject placement = imported.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		imported.remove("dimension_selectors");
		imported.addProperty("block", "minecraft:nether_quartz_ore");
		JsonObject dimensions = new JsonObject();
		dimensions.add(Level.NETHER.location().toString(), placement);
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/quartz_ore", imported);
		Object baked = bake(profile);
		assertTrue(takeover(baked, Level.NETHER, Blocks.NETHER_QUARTZ_ORE));
		assertFalse(takeover(baked, Level.OVERWORLD, Blocks.NETHER_QUARTZ_ORE));
		assertFalse(takeover(baked, Level.END, Blocks.NETHER_QUARTZ_ORE));
	}

	@Test
	void importedDiamondDoesNotRemoveTheIndependentIronDefault() throws Exception {
		JsonObject profile = profile(true);
		JsonObject iron = rule(true, true);
		iron.addProperty("block", "minecraft:iron_ore");
		profile.getAsJsonObject("ores").add("minecraft:iron_ore", iron);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		Object[] ores = ores(bake(profile), Level.OVERWORLD);
		assertEquals(2, ores.length);
		for (Object ore : ores) assertNotEquals(ResourceLocation.parse("minecraft:diamond_ore"), field(ore, "ruleId"));
	}

	@Test
	void intentionalLegacyDeclarationsAreNotCollapsedIntoOneRule() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/a/diamond_ore", rule(false, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/b/diamond_ore", rule(false, true));
		Object baked = bake(profile);
		Object[] ores = ores(baked, Level.OVERWORLD);
		assertEquals(2, ores.length);
		for (Object ore : ores) assertNotEquals(ResourceLocation.parse("minecraft:diamond_ore"), field(ore, "ruleId"));
		assertTrue(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void aDeepslateImportReplacesItsStoneFamilyDefault() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		JsonObject imported = rule(false, true);
		imported.addProperty("block", "minecraft:deepslate_diamond_ore");
		profile.getAsJsonObject("ores").add("orespawn:legacy/deepslate_diamond", imported);
		Object baked = bake(profile);
		assertEquals(1, ores(baked, Level.OVERWORLD).length);
		assertEquals(ResourceLocation.parse("orespawn:legacy/deepslate_diamond"),
				field(ores(baked, Level.OVERWORLD)[0], "ruleId"));
		assertTrue(takeover(baked, Level.OVERWORLD, Blocks.DIAMOND_ORE));
		assertTrue(takeover(baked, Level.OVERWORLD, Blocks.DEEPSLATE_DIAMOND_ORE));
	}

	@Test
	void importedBuiltInPatternsKeepOldSavedPlacementChoices() throws Exception {
		for (String pattern : new String[] { "default", "vein", "normal_cloud", "precision", "clusters", "underfluids" }) {
			JsonObject profile = profile(true);
			JsonObject imported = rule(false, true);
			imported.addProperty("material", "orespawn:diamond");
			JsonObject placement = imported.getAsJsonObject("dimension_selectors")
					.getAsJsonObject("orespawn:all_except_nether_end");
			placement.addProperty("pattern", "orespawn:" + pattern);
			JsonObject dimensions = new JsonObject();
			dimensions.add("minecraft:overworld", placement);
			imported.remove("dimension_selectors");
			imported.add("dimensions", dimensions);
			profile.getAsJsonObject("ores").add("orespawn:legacy/diamond", imported);
			JsonObject policy = new JsonObject();
			policy.addProperty("mode", "consolidated");
			JsonObject outputs = new JsonObject();
			outputs.addProperty("orespawn:legacy/diamond", 1.0D);
			policy.add("outputs", outputs);
			JsonObject placements = new JsonObject();
			placements.addProperty("orespawn:" + pattern, "orespawn:legacy/diamond");
			policy.add("placement_sources", placements);
			JsonObject policies = new JsonObject();
			policies.add("orespawn:diamond|minecraft:overworld", policy);
			profile.add(OreSourcePolicies.SECTION, policies);
			String saved = profile.toString();

			Object[] selected = ores(bake(profile), Level.OVERWORLD);
			assertEquals(1, selected.length, pattern);
			assertEquals(ResourceLocation.parse("orespawn:standard"), field(selected[0], "channel"));
			assertEquals(saved, profile.toString(), "Baking must not rewrite old channel choices");
		}
	}

	@Test
	void explicitChannelsAndUnknownPatternsAreNotCollapsed() {
		for (String pattern : new String[] { "example:clusters", "orespawn:custom_vein" }) {
			JsonObject rule = new JsonObject();
			rule.addProperty("pattern", pattern);
			assertEquals(ResourceLocation.parse(pattern), OreSourcePolicies.placementChannel(rule));
		}
		JsonObject rule = new JsonObject();
		rule.addProperty("pattern", "orespawn:default");
		rule.addProperty("placement_channel", "orespawn:default");
		assertEquals(ResourceLocation.parse("orespawn:default"), OreSourcePolicies.placementChannel(rule));
	}

	private static JsonObject profile(boolean manageVanilla) {
		JsonObject profile = new JsonObject();
		profile.addProperty("manage_vanilla_ores", manageVanilla);
		profile.add("ores", new JsonObject());
		return profile;
	}

	private static JsonObject rule(boolean nativeGeneration, boolean enabled) {
		JsonObject ore = new JsonObject();
		ore.addProperty("block", "minecraft:diamond_ore");
		ore.addProperty("native_generation", nativeGeneration);
		ore.addProperty("enabled", enabled);
		ore.addProperty("retrogen", false);
		JsonObject placement = new JsonObject();
		placement.addProperty("frequency", nativeGeneration ? 1.0D : 0.375D);
		placement.addProperty("min_y", nativeGeneration ? 0 : 7);
		placement.addProperty("max_y", nativeGeneration ? 15 : 11);
		placement.addProperty("quantity", nativeGeneration ? 8 : 4);
		JsonArray hosts = new JsonArray();
		hosts.add(new JsonPrimitive("minecraft:stone"));
		placement.add("host_blocks", hosts);
		JsonObject dimensions = new JsonObject();
		dimensions.add(nativeGeneration ? "minecraft:overworld" : "orespawn:all_except_nether_end", placement);
		ore.add(nativeGeneration ? "dimensions" : "dimension_selectors", dimensions);
		return ore;
	}

	private static Object bake(JsonObject profile) throws Exception {
		Method geology = GeomeConfig.class.getDeclaredMethod("bake", JsonObject.class, ResourceLocation.class);
		geology.setAccessible(true);
		Object config = geology.invoke(null, new JsonObject(), Level.OVERWORLD.location());
		Method bake = OreSpawnOreGeneration.class.getDeclaredMethod("bakeOres", JsonObject.class, BakedGeomeConfig.class);
		bake.setAccessible(true);
		return bake.invoke(null, profile, config);
	}

	@SuppressWarnings("unchecked")
	private static Object[] ores(Object baked, ResourceKey<Level> dimension) throws Exception {
		Object[] explicit = ((Map<ResourceKey<Level>, Object[]>) field(baked, "byDimension")).get(dimension);
		return explicit != null ? explicit : OreSpawnOreGeneration.selectorAllows(dimension)
				? (Object[]) field(baked, "selectorOres") : new Object[0];
	}

	@SuppressWarnings("unchecked")
	private static boolean takeover(Object baked, ResourceKey<Level> dimension, Block block) throws Exception {
		Set<Block> explicit = ((Map<ResourceKey<Level>, Set<Block>>) field(baked, "vanillaOutputs")).get(dimension);
		return explicit != null ? explicit.contains(block) : OreSpawnOreGeneration.selectorAllows(dimension)
				&& ((Set<Block>) field(baked, "selectorVanillaOutputs")).contains(block);
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}
