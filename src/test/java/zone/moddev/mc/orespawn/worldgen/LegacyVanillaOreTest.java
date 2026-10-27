package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.api.OreSpawnPatternRegistry;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class LegacyVanillaOreTest {

	@BeforeAll
	static void bootstrap() {
		Forge14TestBootstrap.registerVanilla();
		if (OreSpawnPatterns.registry().getValue(new ResourceLocation("orespawn:default")) == null) {
			OreSpawnPatterns.registerTypes(new RegistryEvent.Register<>(
					OreSpawnPatternRegistry.REGISTRY_NAME, OreSpawnPatterns.registry()));
		}
	}

	@Test
	void importedDiamondStaysInactiveWhileVanillaManagementIsOff() throws Exception {
		JsonObject profile = profile(false);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		String original = profile.toString();

		Object baked = bake(profile);

		assertEquals(0, ores(baked, WorldIds.OVERWORLD).length);
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.IRON_ORE));
		assertFalse(takeover(baked, WorldIds.NETHER, Blocks.DIAMOND_ORE));
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
		for (ResourceLocation dimension : new ResourceLocation[] {
				WorldIds.OVERWORLD, WorldIds.NETHER, WorldIds.END }) {
			dimensions.add(dimension.toString(), new com.google.gson.JsonParser().parse(placement.toString()));
		}
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", imported);
		String original = profile.toString();

		Object baked = bake(profile);

		for (ResourceLocation dimension : new ResourceLocation[] {
				WorldIds.OVERWORLD, WorldIds.NETHER, WorldIds.END }) {
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

		assertEquals(1, ores(bake(profile), WorldIds.OVERWORLD).length);
		profile.addProperty("manage_vanilla_ores", false);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, WorldIds.OVERWORLD).length);
		assertFalse(takeover(disabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		Object[] restored = ores(bake(profile), WorldIds.OVERWORLD);
		assertEquals(1, restored.length);
		assertEquals(new ResourceLocation("orespawn:legacy/diamond_ore"), field(restored[0], "ruleId"));
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

		assertEquals(0, ores(baked, WorldIds.OVERWORLD).length);
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		assertFalse(profile.has("manage_vanilla_ores"));
	}

	@Test
	void importedSettingsWinOverModernDefaultsWhenBothAreEnabled() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));

		Object[] ores = ores(bake(profile), WorldIds.OVERWORLD);

		assertEquals(1, ores.length, "the native default must not add a second placement pass");
		assertEquals(new ResourceLocation("orespawn:legacy/diamond_ore"), field(ores[0], "ruleId"));
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

		Object[] ores = ores(bake(profile), WorldIds.OVERWORLD);

		assertEquals(1, ores.length);
		assertEquals(new ResourceLocation("minecraft:diamond_ore"), field(ores[0], "ruleId"));
		profile.addProperty("manage_vanilla_ores", false);
		assertFalse(takeover(bake(profile), WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
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

		assertEquals(new ResourceLocation("minecraft:diamond_ore"),
				field(ores(baked, WorldIds.OVERWORLD)[0], "ruleId"));
		assertTrue(takeover(baked, new ResourceLocation("examplemod:moon"), Blocks.DIAMOND_ORE));
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

		assertEquals(1, ores(baked, WorldIds.OVERWORLD).length);
		assertEquals(new ResourceLocation("minecraft:diamond_ore"),
				field(ores(baked, WorldIds.OVERWORLD)[0], "ruleId"));
		assertTrue(takeover(baked, new ResourceLocation("examplemod:moon"), Blocks.DIAMOND_ORE));
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
			placements.addProperty("orespawn:default", selected);
			policy.add("placement_sources", placements);
			JsonObject policies = new JsonObject();
			policies.add(OreSourcePolicies.key(new ResourceLocation("minecraft:diamond"),
					WorldIds.OVERWORLD), policy);
			profile.add(OreSourcePolicies.SECTION, policies);

			Object[] ores = ores(bake(profile), WorldIds.OVERWORLD);

			assertEquals(1, ores.length, selected);
			assertEquals(new ResourceLocation(selected), field(ores[0], "ruleId"));
			profile.addProperty("manage_vanilla_ores", false);
			String saved = profile.toString();
			Object disabled = bake(profile);
			assertEquals(0, ores(disabled, WorldIds.OVERWORLD).length, selected);
			assertFalse(takeover(disabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
			assertEquals(saved, profile.toString());
		}
	}

	@Test
	void ordinaryAddOnRulesKeepTheirExistingBehaviour() throws Exception {
		JsonObject profile = profile(false);
		profile.getAsJsonObject("ores").add("examplemod:diamond_ore", rule(false, true));

		Object baked = bake(profile);

		assertEquals(1, ores(baked, WorldIds.OVERWORLD).length);
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void providerOwnedImportsRespectTheSameManagementSwitch() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("source_mod", "basemetals");
		profile.getAsJsonObject("ores").add("basemetals:legacy/diamond_ore", imported);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, WorldIds.OVERWORLD).length);
		assertFalse(takeover(disabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		Object enabled = bake(profile);
		assertEquals(1, ores(enabled, WorldIds.OVERWORLD).length);
		assertTrue(takeover(enabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void importedNonVanillaBlocksDoNotRequireVanillaManagement() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("block", "minecraft:gold_block");
		profile.getAsJsonObject("ores").add("orespawn:legacy/gold_block", imported);
		Object baked = bake(profile);
		assertEquals(1, ores(baked, WorldIds.OVERWORLD).length);
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.GOLD_BLOCK));
	}

	@Test
	void anImportMappedToAModernProviderIdStillRespectsTheSwitch() throws Exception {
		JsonObject profile = profile(false);
		JsonObject imported = rule(false, true);
		imported.addProperty("source_mod", "legacy");
		profile.getAsJsonObject("ores").add("basemetals:custom_diamond", imported);
		Object disabled = bake(profile);
		assertEquals(0, ores(disabled, WorldIds.OVERWORLD).length);
		assertFalse(takeover(disabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		profile.addProperty("manage_vanilla_ores", true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		Object enabled = bake(profile);
		Object[] ores = ores(enabled, WorldIds.OVERWORLD);
		assertEquals(1, ores.length);
		assertEquals(new ResourceLocation("basemetals:custom_diamond"), field(ores[0], "ruleId"));
		assertTrue(takeover(enabled, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
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
	void allEightImportedVanillaOresRespectOffAndOn() throws Exception {
		Block[] blocks = { Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
				Blocks.LAPIS_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.QUARTZ_ORE };
		for (Block block : blocks) {
			JsonObject profile = profile(false);
			JsonObject imported = rule(false, true);
			imported.addProperty("block", block.getRegistryName().toString());
			profile.getAsJsonObject("ores").add("orespawn:legacy/" + block.getRegistryName().getPath(), imported);
			Object disabled = bake(profile);
			assertEquals(0, ores(disabled, WorldIds.OVERWORLD).length, block.getRegistryName().toString());
			assertFalse(takeover(disabled, WorldIds.OVERWORLD, block), block.getRegistryName().toString());
			profile.addProperty("manage_vanilla_ores", true);
			Object enabled = bake(profile);
			assertEquals(1, ores(enabled, WorldIds.OVERWORLD).length, block.getRegistryName().toString());
			assertTrue(takeover(enabled, WorldIds.OVERWORLD, block), block.getRegistryName().toString());
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
			assertEquals(0, ores(baked, WorldIds.OVERWORLD).length);
			assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
		}
	}

	@Test
	void importedNetherQuartzDoesNotTakeOverOtherDimensions() throws Exception {
		JsonObject profile = profile(true);
		JsonObject imported = rule(false, true);
		JsonObject placement = imported.getAsJsonObject("dimension_selectors")
				.getAsJsonObject("orespawn:all_except_nether_end");
		imported.remove("dimension_selectors");
		imported.addProperty("block", "minecraft:quartz_ore");
		JsonObject dimensions = new JsonObject();
		dimensions.add(WorldIds.NETHER.toString(), placement);
		imported.add("dimensions", dimensions);
		profile.getAsJsonObject("ores").add("orespawn:legacy/quartz_ore", imported);
		Object baked = bake(profile);
		assertTrue(takeover(baked, WorldIds.NETHER, Blocks.QUARTZ_ORE));
		assertFalse(takeover(baked, WorldIds.OVERWORLD, Blocks.QUARTZ_ORE));
		assertFalse(takeover(baked, WorldIds.END, Blocks.QUARTZ_ORE));
	}

	@Test
	void importedDiamondDoesNotRemoveTheIndependentIronDefault() throws Exception {
		JsonObject profile = profile(true);
		JsonObject iron = rule(true, true);
		iron.addProperty("block", "minecraft:iron_ore");
		profile.getAsJsonObject("ores").add("minecraft:iron_ore", iron);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		Object[] ores = ores(bake(profile), WorldIds.OVERWORLD);
		assertEquals(2, ores.length);
		for (Object ore : ores) assertNotEquals(new ResourceLocation("minecraft:diamond_ore"), field(ore, "ruleId"));
	}

	@Test
	void intentionalLegacyDeclarationsAreNotCollapsedIntoOneRule() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("minecraft:diamond_ore", rule(true, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/a/diamond_ore", rule(false, true));
		profile.getAsJsonObject("ores").add("orespawn:legacy/b/diamond_ore", rule(false, true));
		Object baked = bake(profile);
		Object[] ores = ores(baked, WorldIds.OVERWORLD);
		assertEquals(2, ores.length);
		for (Object ore : ores) assertNotEquals(new ResourceLocation("minecraft:diamond_ore"), field(ore, "ruleId"));
		assertTrue(takeover(baked, WorldIds.OVERWORLD, Blocks.DIAMOND_ORE));
	}

	@Test
	void importedDefaultPatternReceivesHybridAndReplacementScalingWithoutChangingItsSettings() throws Exception {
		JsonObject profile = profile(true);
		profile.getAsJsonObject("ores").add("orespawn:legacy/diamond_ore", rule(false, true));
		String before = profile.toString();
		Object[] imported = ores(bake(profile), WorldIds.OVERWORLD);

		assertEquals(1, imported.length);
		ResourceLocation channel = (ResourceLocation) field(imported[0], "channel");
		ResourceLocation material = new ResourceLocation("orespawn:diamond");

		assertEquals(OreSourcePolicies.STANDARD, channel);
		assertEquals(0.15D, OreSpawnOreGeneration.channelBackgroundScale(material, channel, 0.15D, 1.0D));
		assertEquals(0.0D, OreSpawnOreGeneration.channelBackgroundScale(material, channel, 0.0D, 1.0D));
		assertEquals(0.375D, field(imported[0], "frequency"));
		assertEquals(7, field(imported[0], "minY"));
		assertEquals(11, field(imported[0], "maxY"));
		assertEquals(false, field(imported[0], "retrogen"));
		assertEquals(before, profile.toString());
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
		placement.addProperty("pattern", "orespawn:default");
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
		Object config = geology.invoke(null, new JsonObject(), WorldIds.OVERWORLD);
		Method bake = OreSpawnOreGeneration.class.getDeclaredMethod("bakeOres", JsonObject.class, BakedGeomeConfig.class);
		bake.setAccessible(true);
		return bake.invoke(null, profile, config);
	}

	@SuppressWarnings("unchecked")
	private static Object[] ores(Object baked, ResourceLocation dimension) throws Exception {
		Object[] explicit = ((Map<ResourceLocation, Object[]>) field(baked, "byDimension")).get(dimension);
		return explicit != null ? explicit : (Object[]) field(baked, "selectorOres");
	}

	@SuppressWarnings("unchecked")
	private static boolean takeover(Object baked, ResourceLocation dimension, Block block) throws Exception {
		Set<Block> explicit = ((Map<ResourceLocation, Set<Block>>) field(baked, "vanillaOutputs")).get(dimension);
		return explicit != null ? explicit.contains(block) : OreSpawnOreGeneration.selectorAllows(dimension)
				&& ((Set<Block>) field(baked, "selectorVanillaOutputs")).contains(block);
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}
