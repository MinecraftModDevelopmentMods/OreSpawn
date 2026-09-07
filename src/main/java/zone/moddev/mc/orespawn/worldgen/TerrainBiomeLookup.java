package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.server.ServerWorld;

/**
 * Internal generation-time biome lookup shared by geology and its public
 * read-only sampler.
 */
public final class TerrainBiomeLookup {
	private TerrainBiomeLookup() {
	}

	public static Biome atBlock(BiomeContainer source,
			int blockX, int blockY, int blockZ) {
		return source.getNoiseBiome(quart(blockX), quart(blockY), quart(blockZ));
	}

	public static Biome atBlock(ServerWorld level,
			int blockX, int blockY, int blockZ) {
		return level.getNoiseBiomeRaw(quart(blockX), quart(blockY), quart(blockZ));
	}

	static int quart(int blockCoordinate) {
		return Math.floorDiv(blockCoordinate, 4);
	}
}
