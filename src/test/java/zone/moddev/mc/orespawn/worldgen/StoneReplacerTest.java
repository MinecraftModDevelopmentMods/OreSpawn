package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.block.Blocks;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome.Category;

class StoneReplacerTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.register();
	}

	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, true));
		assertFalse(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.STONE.getDefaultState()));
		assertTrue(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.GRANITE.getDefaultState()));
	}

	@Test
	void nonOverworldBiomesAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.NETHER, true, true));
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.THEEND, true, true));
	}

	@Test
	void invalidTerrainHostsRemainUnsafeEvenWhenDeclared() {
		LinkedHashSet<net.minecraft.block.Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				new net.minecraft.util.ResourceLocation("surfaceprobe:the_end"),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.getDefaultState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.getDefaultState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.getDefaultState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.getDefaultState()));
	}
}
