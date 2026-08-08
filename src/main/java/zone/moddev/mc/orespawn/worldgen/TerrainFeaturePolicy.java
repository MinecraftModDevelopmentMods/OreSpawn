package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.biome.Biome;


final class TerrainFeaturePolicy {
	private TerrainFeaturePolicy() { }

	static boolean shouldRemoveVanillaMatchingStoneFeatures(Biome.Category category,
			boolean rockPlacementEnabled, boolean overworldTerrainConfigured) {
		return rockPlacementEnabled && overworldTerrainConfigured
				&& category != Biome.Category.NETHER && category != Biome.Category.THEEND
				&& category != Biome.Category.NONE;
	}
}
