package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.test.Forge28TestBootstrap;

class NativeOreHostTest {
	private static final ResourceLocation NETHERRACK = new ResourceLocation("forge:netherrack");
	private TagCollection<Block> previousTags;

	@BeforeAll
	static void bootstrap() {
		Forge28TestBootstrap.registerVanilla();
		zone.moddev.mc.orespawn.init.OreSpawnPatterns.registry().register(
				zone.moddev.mc.orespawn.api.OrePatternType.create(
						zone.moddev.mc.orespawn.api.StandardPatternSettings.CODEC,
						settings -> context -> false)
						.setRegistryName("example", "host_test"));
	}

	@BeforeEach
	void isolateTags() {
		previousTags = BlockTags.getCollection();
		BlockTags.setCollection(new TagCollection<>(id -> Optional.empty(), "tags/blocks", false, "block"));
	}

	@AfterEach
	void restoreTags() {
		BlockTags.setCollection(previousTags);
	}

	@Test
	void forge28ShipsTheDefaultQuartzHost() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/data/forge/tags/blocks/netherrack.json")) {
			assertNotNull(input, "Forge 28 must supply the tag used by the saved quartz defaults");
			JsonObject json = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
					.getAsJsonObject();
			assertEquals("minecraft:netherrack", json.getAsJsonArray("values").get(0).getAsString());
			Map<ResourceLocation, Tag.Builder<Block>> tags = new HashMap<>();
			tags.put(NETHERRACK, Tag.Builder.<Block>create().fromJson(
					id -> "minecraft:netherrack".equals(id.toString())
							? Optional.of(Blocks.NETHERRACK) : Optional.empty(), json));
			BlockTags.getCollection().registerAll(tags);
			assertEquals(Collections.singleton(Blocks.NETHERRACK), resolve(NETHERRACK));
		}
	}

	@Test
	void unknownHostDoesNotGuessAReplacement() throws Exception {
		assertTrue(resolve(new ResourceLocation("example:missing_host")).isEmpty());
		assertTrue(BlockTags.getCollection().getTagMap().isEmpty(), "Resolution must not register a tag");
	}

	@Test
	void installedNetherTagRemainsAuthoritative() throws Exception {
		install(Tag.Builder.<Block>create().add(Blocks.END_STONE));
		assertEquals(Collections.singleton(Blocks.END_STONE), resolve(NETHERRACK));
	}

	@Test
	void deliberatelyEmptyNetherTagRemainsEmpty() throws Exception {
		install(Tag.Builder.<Block>create());
		assertTrue(resolve(NETHERRACK).isEmpty());
	}

	@Test
	void quartzDefaultsUseTheShippedHostTag() throws Exception {
		Method defaults = GeomeConfig.class.getDeclaredMethod("defaultOreConfig");
		defaults.setAccessible(true);
		JsonObject ores = (JsonObject) defaults.invoke(null);
		JsonObject rule = ores.getAsJsonObject("minecraft:nether_quartz_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether");
		assertEquals("forge:netherrack", rule.getAsJsonArray("host_tags").get(0).getAsString());
	}

	@Test
	void newNetherOreRulesUseTheShippedHostTag() throws Exception {
		assertNetherEditorHost("OreEntryScreen");
	}

	@Test
	void newNetherFluidRulesUseTheShippedHostTag() throws Exception {
		assertNetherEditorHost("FluidDepositEntryScreen");
	}

	@Test
	void tagRetryPreservesWorkingFamilyHosts() throws Exception {
		JsonObject rule = rule();
		JsonArray families = new JsonArray();
		families.add("sedimentary");
		rule.add("host_families", families);
		assertOreHostsRemainStable(rule);
	}

	@Test
	void tagRetryPreservesWorkingExplicitHosts() throws Exception {
		JsonObject rule = rule();
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:netherrack");
		rule.add("host_blocks", hosts);
		assertOreHostsRemainStable(rule);
	}

	@Test
	void tagOnlyOreRulesRetryLoadedTagsAndRespectAnEmptyOverride() throws Exception {
		Map<String, Object> early = bakeOres(rule(), Collections.emptyMap());
		assertTrue(early.isEmpty());
		install(Tag.Builder.<Block>create().add(Blocks.NETHERRACK));
		Map<String, Object> ready = bakeOres(rule(), early);
		assertEquals(1, ready.size());
		assertTrue(hosts(ready.values().iterator().next()).containsKey(Blocks.NETHERRACK));
		install(Tag.Builder.<Block>create());
		assertTrue(bakeOres(rule(), ready).isEmpty());
	}

	@Test
	void fluidTagRetryPreservesWorkingExplicitHosts() throws Exception {
		JsonObject rule = rule();
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:netherrack");
		rule.add("host_blocks", hosts);
		Map<String, Object> early = bakeFluids(rule, Collections.emptyMap());
		assertEquals(1, early.size());
		install(Tag.Builder.<Block>create().add(Blocks.END_STONE));
		Map<String, Object> ready = bakeFluids(rule, early);
		assertEquals(early, ready, "A working deposit must retain its original host selection");
	}

	@Test
	void tagOnlyFluidRulesRetryLoadedTagsAndRespectAnEmptyOverride() throws Exception {
		Map<String, Object> early = bakeFluids(rule(), Collections.emptyMap());
		assertTrue(early.isEmpty());
		install(Tag.Builder.<Block>create().add(Blocks.NETHERRACK));
		Map<String, Object> ready = bakeFluids(rule(), early);
		assertEquals(1, ready.size());
		install(Tag.Builder.<Block>create());
		assertTrue(bakeFluids(rule(), ready).isEmpty());
	}

	private static void assertOreHostsRemainStable(JsonObject rule) throws Exception {
		Map<String, Object> early = bakeOres(rule, Collections.emptyMap());
		assertEquals(1, early.size());
		install(Tag.Builder.<Block>create().add(Blocks.END_STONE));
		Map<String, Object> ready = bakeOres(rule, early);
		assertEquals(hosts(early.values().iterator().next()), hosts(ready.values().iterator().next()));
		assertFalse(hosts(ready.values().iterator().next()).containsKey(Blocks.END_STONE));
	}

	private static JsonObject rule() {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", 127);
		rule.addProperty("frequency", 1);
		rule.addProperty("quantity", 14);
		rule.addProperty("pattern", "example:host_test");
		JsonArray tags = new JsonArray();
		tags.add("forge:netherrack");
		rule.add("host_tags", tags);
		return rule;
	}

	private static JsonObject profile(String section, String key, JsonObject rule) {
		JsonObject root = new JsonObject();
		JsonObject entries = new JsonObject();
		JsonObject entry = new JsonObject();
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:the_nether", rule);
		entry.add("dimensions", dimensions);
		entry.addProperty("block", section.equals("ores") ? "minecraft:iron_ore" : "minecraft:water");
		entries.add(key, entry);
		root.add(section, entries);
		return root;
	}

	private static Map<String, Object> bakeOres(JsonObject rule, Map<String, Object> previous) throws Exception {
		Method bake = OreSpawnOreGeneration.class.getDeclaredMethod("bakeOres", JsonObject.class,
				BakedGeomeConfig.class, Map.class, Map.class);
		bake.setAccessible(true);
		Map<String, Object> resolved = new HashMap<>();
		bake.invoke(null, profile("ores", "example:ore", rule), config(), previous, resolved);
		return resolved;
	}

	private static Map<String, Object> bakeFluids(JsonObject rule, Map<String, Object> previous) throws Exception {
		Method bake = FluidDepositFeature.class.getDeclaredMethod("bakeDeposits", JsonObject.class,
				Map.class, Map.class);
		bake.setAccessible(true);
		Map<String, Object> resolved = new HashMap<>();
		bake.invoke(null, profile("fluid_deposits", "example:fluid", rule), previous, resolved);
		return resolved;
	}

	@SuppressWarnings("unchecked")
	private static Map<Block, Double> hosts(Object ore) throws Exception {
		java.lang.reflect.Field field = ore.getClass().getDeclaredField("hostBlocks");
		field.setAccessible(true);
		return (Map<Block, Double>) field.get(ore);
	}

	private static BakedGeomeConfig config() {
		BakedGeomeConfig.GeomeDefinition[] geomes = {
				new BakedGeomeConfig.GeomeDefinition("example:geome", 1,
						new double[] { 1, 1, 1, 1 })
		};
		BakedGeomeConfig.RockEntry[] rocks = {
				new BakedGeomeConfig.RockEntry(Blocks.STONE.getDefaultState(), RockFamily.SEDIMENTARY,
						64, 64, 0, 255, 1, true, new double[] { 1 })
		};
		FormationSettings formations = new FormationSettings(FormationSettings.Algorithm.STABLE_LAYERS,
				256, 100, 8, 48, 64, 12, 2, 0.85);
		return new BakedGeomeConfig(geomes, 384, 1.15, 0.9, 0.45,
				Collections.emptyMap(), Collections.emptyMap(), rocks, formations);
	}

	private static void install(Tag.Builder<Block> builder) {
		Map<ResourceLocation, Tag.Builder<Block>> tags = new HashMap<>();
		tags.put(NETHERRACK, builder);
		BlockTags.getCollection().registerAll(tags);
	}

	private static void assertNetherEditorHost(String name) throws Exception {
		Class<?> screen = Class.forName("zone.moddev.mc.orespawn.client." + name);
		Method defaults = screen.getDeclaredMethod("defaultDimension", String.class);
		defaults.setAccessible(true);
		JsonObject rule = (JsonObject) defaults.invoke(null, "minecraft:the_nether");
		assertEquals("forge:netherrack", rule.getAsJsonArray("host_tags").get(0).getAsString());
	}

	@SuppressWarnings("unchecked")
	private static Set<Block> resolve(ResourceLocation id) throws Exception {
		Method resolver = OreSpawnOreGeneration.class.getDeclaredMethod("resolveTag", ResourceLocation.class);
		resolver.setAccessible(true);
		return (Set<Block>) resolver.invoke(null, id);
	}
}
