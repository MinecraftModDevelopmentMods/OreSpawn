package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome.Category;

class StoneReplacerTest {
	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, true));
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
		LinkedHashSet<Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				RegistryKey.create(Registry.DIMENSION_REGISTRY,
						new ResourceLocation("surfaceprobe", "the_end")),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.defaultBlockState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.defaultBlockState()));
	}
}
