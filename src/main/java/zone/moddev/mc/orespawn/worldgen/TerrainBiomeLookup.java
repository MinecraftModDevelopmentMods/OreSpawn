package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;

/**
 * Internal generation-time biome lookup shared by geology and its public
 * read-only sampler.
 */
public final class TerrainBiomeLookup {
	private TerrainBiomeLookup() {
	}

	public static Biome atBlock(BiomeManager.NoiseBiomeSource source,
			int blockX, int blockY, int blockZ) {
		return source.getNoiseBiome(QuartPos.fromBlock(blockX),
				QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ));
	}

	public static Biome atBlock(BiomeManager manager,
			int blockX, int blockY, int blockZ) {
		return manager.getNoiseBiomeAtQuart(QuartPos.fromBlock(blockX),
				QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ));
	}
}
