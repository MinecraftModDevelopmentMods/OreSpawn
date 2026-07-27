package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.level.biome.Biome.BiomeCategory;

final class TerrainFeaturePolicy {
	private TerrainFeaturePolicy() { }

	static boolean shouldRemoveVanillaMatchingStoneFeatures(BiomeCategory category,
			boolean rockPlacementEnabled, boolean overworldTerrainConfigured) {
		return rockPlacementEnabled && overworldTerrainConfigured
				&& category != BiomeCategory.NETHER && category != BiomeCategory.THEEND
				&& category != BiomeCategory.NONE;
	}
}
