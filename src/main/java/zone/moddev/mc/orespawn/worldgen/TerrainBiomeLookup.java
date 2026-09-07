package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;

/**
 * Internal generation-time biome lookup shared by geology and its public
 * read-only sampler.
 */
public final class TerrainBiomeLookup {
	private TerrainBiomeLookup() {
	}

	public static Biome atBlock(BiomeManager.IBiomeReader source,
			int blockX, int blockY, int blockZ) {
		return source.getNoiseBiome(Math.floorDiv(blockX, 4),
				Math.floorDiv(blockY, 4), Math.floorDiv(blockZ, 4));
	}

	public static Biome atBlock(BiomeManager manager,
			int blockX, int blockY, int blockZ) {
		return manager.getNoiseBiomeAtQuart(Math.floorDiv(blockX, 4),
				Math.floorDiv(blockY, 4), Math.floorDiv(blockZ, 4));
	}
}
