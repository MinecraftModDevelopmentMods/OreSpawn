package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

class StoneReplacerTest {
	@Test
	void ordinarySolidRocksCanUseTheSectionFastPath() {
		assertTrue(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.GRANITE.defaultBlockState()));
	}

	@Test
	void heightOrLightChangingOutputsUseMinecraftsFullUpdatePath() {
		assertFalse(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()));
		assertFalse(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState()));
	}

	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.OVERWORLD, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.OVERWORLD, true, true));
	}

	@Test
	void netherAndEndAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.NETHER, true, true));
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.END, true, true));
	}

	@Test
	void explicitlyConfiguredCustomDimensionsCanSuppressMatchingStoneFeatures() {
		ResourceKey<Level> moon = ResourceKey.create(Registries.DIMENSION,
				Identifier.fromNamespaceAndPath("examplemod", "moon"));
		assertTrue(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				moon, true, true));
	}

	@Test
	void invalidTerrainHostsRemainUnsafeEvenWhenDeclared() {
		LinkedHashSet<Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				ResourceKey.create(Registries.DIMENSION,
						Identifier.fromNamespaceAndPath("surfaceprobe", "the_end")),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.defaultBlockState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.defaultBlockState()));
	}
}
