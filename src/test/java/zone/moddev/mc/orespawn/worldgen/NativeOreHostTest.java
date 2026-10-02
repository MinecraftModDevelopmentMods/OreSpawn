package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagCollection;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class NativeOreHostTest {
	private static final ResourceLocation NETHERRACK = new ResourceLocation("forge:netherrack");
	private TagCollection<Block> previousTags;

	@BeforeAll
	static void bootstrap() {
		Forge25TestBootstrap.registerVanilla();
	}

	@BeforeEach
	void isolateTags() {
		previousTags = BlockTags.getCollection();
		BlockTags.setCollection(new TagCollection<>(id -> true, id -> null, "tags/blocks", false, "block"));
	}

	@AfterEach
	void restoreTags() {
		BlockTags.setCollection(previousTags);
	}

	@Test
	void missingLegacyNetherHostStillResolvesNativeNetherrack() throws Exception {
		assertEquals(Collections.singleton(Blocks.NETHERRACK), resolve(NETHERRACK));
		assertTrue(BlockTags.getCollection().getTagMap().isEmpty(), "Resolution must not register a tag");
	}

	@Test
	void unknownCustomHostDoesNotGuessAReplacement() throws Exception {
		assertTrue(resolve(new ResourceLocation("example:missing_host")).isEmpty());
	}

	@Test
	void definedNetherTagRemainsAuthoritative() throws Exception {
		BlockTags.getCollection().register(Tag.Builder.<Block>create().add(Blocks.END_STONE).build(NETHERRACK));
		assertEquals(Collections.singleton(Blocks.END_STONE), resolve(NETHERRACK));
	}

	@Test
	void deliberatelyEmptyNetherTagRemainsEmpty() throws Exception {
		BlockTags.getCollection().register(new Tag<Block>(NETHERRACK));
		assertTrue(resolve(NETHERRACK).isEmpty());
	}

	@Test
	void newQuartzDefaultsUseAnInstalledBlockRatherThanAnAbsentTag() throws Exception {
		Method defaults = GeomeConfig.class.getDeclaredMethod("defaultOreConfig");
		defaults.setAccessible(true);
		JsonObject ores = (JsonObject) defaults.invoke(null);
		JsonObject rule = ores.getAsJsonObject("minecraft:nether_quartz_ore")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether");
		assertTrue(rule.has("host_blocks"));
		assertEquals("minecraft:netherrack", rule.getAsJsonArray("host_blocks").get(0).getAsString());
		assertTrue(!rule.has("host_tags"), "New defaults must not depend on Forge shipping this tag");
	}

	@Test
	void newNetherOreRulesUseAnInstalledHost() throws Exception {
		assertNetherEditorHost("OreEntryScreen");
	}

	@Test
	void newNetherFluidRulesUseAnInstalledHost() throws Exception {
		assertNetherEditorHost("FluidDepositEntryScreen");
	}

	private static void assertNetherEditorHost(String name) throws Exception {
		Class<?> screen = Class.forName("zone.moddev.mc.orespawn.client." + name);
		Method defaults = screen.getDeclaredMethod("defaultDimension", String.class);
		defaults.setAccessible(true);
		JsonObject rule = (JsonObject) defaults.invoke(null, "minecraft:the_nether");
		assertEquals("minecraft:netherrack", rule.getAsJsonArray("host_blocks").get(0).getAsString());
		assertTrue(!rule.has("host_tags") || rule.getAsJsonArray("host_tags").size() == 0);
	}

	@SuppressWarnings("unchecked")
	private static Set<Block> resolve(ResourceLocation id) throws Exception {
		Method resolver = OreSpawnOreGeneration.class.getDeclaredMethod("resolveTag", ResourceLocation.class);
		resolver.setAccessible(true);
		return (Set<Block>) resolver.invoke(null, id);
	}
}
