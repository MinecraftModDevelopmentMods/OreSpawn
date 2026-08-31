package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.core.Holder;
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

	public static Holder<Biome> atBlock(BiomeManager.NoiseBiomeSource source,
			int blockX, int blockY, int blockZ) {
		return source.getNoiseBiome(QuartPos.fromBlock(blockX),
				QuartPos.fromBlock(blockY), QuartPos.fromBlock(blockZ));
	}
}
