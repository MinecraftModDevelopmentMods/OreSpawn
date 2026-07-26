package com.mcmoddev.orespawn.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

final class TerrainFeaturePolicy {
	private TerrainFeaturePolicy() { }

	static boolean shouldSuppressVanillaMatchingStoneFeature(ResourceKey<Level> dimension,
			boolean rockPlacementEnabled, boolean overworldTerrainConfigured) {
		return rockPlacementEnabled && overworldTerrainConfigured
				&& !Level.NETHER.equals(dimension) && !Level.END.equals(dimension);
	}
}
