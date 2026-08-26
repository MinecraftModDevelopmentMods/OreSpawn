package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome.BiomeCategory;

class StoneReplacerTest {
	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				BiomeCategory.PLAINS, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				BiomeCategory.PLAINS, true, true));
	}

	@Test
	void nonOverworldBiomesAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				BiomeCategory.NETHER, true, true));
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				BiomeCategory.THEEND, true, true));
	}

	@Test
	void invalidTerrainHostsRemainUnsafeEvenWhenDeclared() {
		LinkedHashSet<Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				ResourceKey.create(Registry.DIMENSION_REGISTRY,
						new ResourceLocation("surfaceprobe", "the_end")),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.defaultBlockState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.defaultBlockState()));
	}
}
