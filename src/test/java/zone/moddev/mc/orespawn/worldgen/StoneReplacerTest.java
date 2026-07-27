package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

class StoneReplacerTest {
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
				new ResourceLocation("examplemod", "moon"));
		assertTrue(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				moon, true, true));
	}
}
