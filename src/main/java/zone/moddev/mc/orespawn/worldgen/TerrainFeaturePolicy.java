package zone.moddev.mc.orespawn.worldgen;

final class TerrainFeaturePolicy {
	private TerrainFeaturePolicy() { }

	static boolean shouldRemoveVanillaMatchingStoneFeatures(int dimension,
			boolean rockPlacementEnabled, boolean overworldTerrainConfigured) {
		return rockPlacementEnabled && overworldTerrainConfigured
				&& dimension != -1 && dimension != 1;
	}
}
