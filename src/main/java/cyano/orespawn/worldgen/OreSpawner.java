package cyano.orespawn.worldgen;

import java.util.Collection;
import java.util.Random;

import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.fml.common.IWorldGenerator;

/** Deprecated OS1 generator declaration routed into OS4 exactly once. */
@Deprecated
public class OreSpawner implements IWorldGenerator {
	private static int ordinal;
	private final OreSpawnData spawnData;
	private final Integer dimension;

	public OreSpawner(Block ore, int size, int variation, float frequency,
			int minY, int maxY, int dimension, long hash) {
		this(ore, 0, size, variation, frequency, minY, maxY, null, dimension, hash);
	}

	public OreSpawner(Block ore, int metadata, int size, int variation, float frequency,
			int minY, int maxY, int dimension, long hash) {
		this(ore, metadata, size, variation, frequency, minY, maxY, null, dimension, hash);
	}

	public OreSpawner(Block ore, int metadata, int size, int variation, float frequency,
			int minY, int maxY, Collection<String> biomes, int dimension, long hash) {
		this(new OreSpawnData(ore, metadata, size, variation, frequency, minY, maxY, biomes),
				Integer.valueOf(dimension), hash);
	}

	public OreSpawner(OreSpawnData spawnData, Integer dimension, long hash) {
		this.spawnData = spawnData;
		this.dimension = dimension;
		ResourceLocation id = spawnData.ore == null ? null : spawnData.ore.getRegistryName();
		String owner = id == null ? "legacy" : id.getResourceDomain();
		LegacyOs3Bridge.registerOs1Spawn(owner, "programmatic_" + (ordinal++), spawnData.bridgeSpawn(dimension));
	}

	/** OS4 owns scheduling; this ABI method intentionally performs no second pass. */
	@Override public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) { }

	public static void spawnOre(BlockPos pos, Block ore, int metadata, int quantity,
			World world, Random random) {
		new WorldGenMinable(ore.getStateFromMeta(metadata), Math.max(1, quantity)).generate(world, random, pos);
	}
}
