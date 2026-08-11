package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.util.ResourceLocation;

/**
 * Forge 1.10 has no biome feature-stage lists. The ordered terrain-event and
 * IWorldGenerator coordinator owns installation, so there is nothing to mutate
 * or restore on individual biome instances.
 */
final class BiomeFeatureInstaller {
	private BiomeFeatureInstaller() {
	}

	static void install(BakedBiomeWorldgen config, ResourceLocation dimension) {
	}

	static void restoreAll() {
	}
}
