package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.tags.TagContainer;
import net.minecraft.tags.StaticTags;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;

class NativeOreHostTest {
	private static final ResourceLocation NETHERRACK = new ResourceLocation("minecraft:base_stone_nether");
	private TagContainer previous;

	@BeforeAll
	static void bootstrap() {
		Forge36TestBootstrap.registerVanilla();
	}

	@BeforeEach
	void isolateTags() {
		// Save actual collections, not named wrappers that would rebind to themselves.
		TagContainer.Builder snapshot = new TagContainer.Builder();
		StaticTags.visitHelpers(helper -> snapshotCollection(snapshot, helper));
		previous = snapshot.build();
		install(Collections.emptySet(), false);
	}

	@AfterEach
	void restoreTags() {
		StaticTags.resetAll(previous);
	}

	@Test
	void forge37ShipsTheDefaultQuartzHost() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/data/minecraft/tags/blocks/base_stone_nether.json")) {
			assertNotNull(input, "Forge must supply the tag used by saved quartz defaults");
			JsonObject json = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
					.getAsJsonObject();
			Set<String> values = new java.util.HashSet<>();
			for (com.google.gson.JsonElement value : json.getAsJsonArray("values")) values.add(value.getAsString());
			assertEquals(Set.of("minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone"), values);
		}
	}

	@Test
	void unknownHostsDoNotGuessOrRegisterATag() throws Exception {
		assertTrue(resolve(OreSpawnOreGeneration.class, new ResourceLocation("example:missing")).isEmpty());
		assertTrue(resolve(FluidDepositFeature.class, new ResourceLocation("example:missing")).isEmpty());
		assertTrue(BlockTags.getAllTags().getAllTags().isEmpty());
	}

	@Test
	void loadedTagsRemainAuthoritativeForOresAndFluids() throws Exception {
		install(Collections.singleton(Blocks.END_STONE), true);
		assertEquals(Collections.singleton(Blocks.END_STONE), resolve(OreSpawnOreGeneration.class, NETHERRACK));
		assertEquals(Collections.singleton(Blocks.END_STONE), resolve(FluidDepositFeature.class, NETHERRACK));
	}

	@Test
	void deliberatelyEmptyTagsRemainEmpty() throws Exception {
		install(Collections.emptySet(), true);
		assertTrue(resolve(OreSpawnOreGeneration.class, NETHERRACK).isEmpty());
		assertTrue(resolve(FluidDepositFeature.class, NETHERRACK).isEmpty());
	}

	@Test
	void quartzDefaultsUseTheShippedHostTag() throws Exception {
		Method defaults = GeomeConfig.class.getDeclaredMethod("defaultOreConfig");
		defaults.setAccessible(true);
		JsonObject ores = (JsonObject) defaults.invoke(null);
		JsonObject rule = ores.getAsJsonObject("minecraft:nether_quartz_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether");
		assertEquals("minecraft:base_stone_nether", rule.getAsJsonArray("host_tags").get(0).getAsString());
	}

	@Test
	void newNetherOreRulesUseTheShippedHostTag() throws Exception {
		assertEditorHost("OreEntryScreen");
	}

	@Test
	void newNetherFluidRulesUseTheShippedHostTag() throws Exception {
		assertEditorHost("FluidDepositEntryScreen");
	}

	@Test
	void tagOnlyFluidsBakeLoadedHostsAndRespectEmptyOverrides() throws Exception {
		assertNull(bakeFluid());
		install(Collections.singleton(Blocks.NETHERRACK), true);
		assertNotNull(bakeFluid());
		install(Collections.emptySet(), true);
		assertNull(bakeFluid());
	}

	private static Object bakeFluid() throws Exception {
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", 24);
		rule.addProperty("max_y", 28);
		rule.addProperty("frequency", 1);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:base_stone_nether");
		rule.add("host_tags", tags);
		Method bake = FluidDepositFeature.class.getDeclaredMethod("bakeDeposit", BlockState.class,
				JsonObject.class, BakedGeomeConfig.class, java.util.Map.class);
		bake.setAccessible(true);
		return bake.invoke(null, Blocks.WATER.defaultBlockState(), rule, null, new HashMap<>());
	}

	private void install(Set<Block> blocks, boolean declared) {
		TagCollection<Block> collection = TagCollection.of(declared
				? Collections.singletonMap(NETHERRACK, Tag.fromSet(blocks)) : Collections.emptyMap());
		StaticTags.resetAll(new TagContainer.Builder().add(Registry.BLOCK_REGISTRY, collection).build());
	}

	private static <T> void snapshotCollection(TagContainer.Builder snapshot,
			net.minecraft.tags.StaticTagHelper<T> helper) {
		snapshot.add(helper.getKey(), helper.getAllTags());
	}

	private static void assertEditorHost(String name) throws Exception {
		Method defaults = Class.forName("zone.moddev.mc.orespawn.client." + name)
				.getDeclaredMethod("defaultDimension", String.class);
		defaults.setAccessible(true);
		JsonObject rule = (JsonObject) defaults.invoke(null, "minecraft:the_nether");
		assertEquals("minecraft:base_stone_nether", rule.getAsJsonArray("host_tags").get(0).getAsString());
	}

	@SuppressWarnings("unchecked")
	private static Set<Block> resolve(Class<?> owner, ResourceLocation id) throws Exception {
		Method resolver = owner.getDeclaredMethod("resolveTag", ResourceLocation.class);
		resolver.setAccessible(true);
		return (Set<Block>) resolver.invoke(null, id);
	}
}
