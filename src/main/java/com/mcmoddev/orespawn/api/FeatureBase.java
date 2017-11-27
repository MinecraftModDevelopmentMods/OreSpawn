package com.mcmoddev.orespawn.api;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.util.OreList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.*;
import java.util.Map.Entry;

public class FeatureBase {
	private static final int MAX_CACHE_SIZE = 2048;
	/** overflow cache so that ores that spawn at edge of chunk can
	 * appear in the neighboring chunk without triggering a chunk-load */
	private static final Map<Vec3i, Map<BlockPos, IBlockState>> overflowCache = new HashMap<>(MAX_CACHE_SIZE);
	private static final Deque<Vec3i> cacheOrder = new LinkedList<>();
	protected Random random;

	public FeatureBase(Random rand) {
		this.random = rand;
	}

	private boolean fullMatch(ImmutableSet<BiomeLocation> locs, Biome biome) {
		for (BiomeLocation b : locs) {
			for (Biome bm : b.getBiomes()) {
				if (bm.equals(biome)) {
					return true;
				}
			}
		}

		return false;
	}

	private boolean biomeMatch(Biome chunkBiome, BiomeLocation inp) {
		if (inp.getBiomes().isEmpty()) {
			return false;
		}

		if (inp instanceof BiomeLocationComposition) {
			BiomeLocationComposition loc = (BiomeLocationComposition) inp;
			boolean exclMatch = fullMatch(loc.getExclusions(), chunkBiome);
			boolean inclMatch = fullMatch(loc.getInclusions(), chunkBiome);

			if ((loc.getInclusions().isEmpty() || inclMatch) && !exclMatch) {
				return false;
			}
		} else if (inp.matches(chunkBiome)) {
			return false;
		}

		return true;
	}

	protected void runCache(int chunkX, int chunkZ, World world, List<IBlockState> blockReplace) {
		Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos, IBlockState> cache = retrieveCache(chunkCoord);

		if (!cache.isEmpty()) {  // if there is something in the cache, try to spawn it
			for (Entry<BlockPos, IBlockState> ent : cache.entrySet()) {
				spawnNoCheck(cache.get(ent.getKey()), world, ent.getKey(), world.provider.getDimension(), blockReplace);
			}
		}
	}

	protected boolean spawn(IBlockState oreBlock, World world, BlockPos coord, int dimension, boolean cacheOverflow,
	    List<IBlockState> blockReplace, BiomeLocation biomes) {
		if (oreBlock == null) {
			OreSpawn.LOGGER.fatal("FeatureBase.spawn() called with a null ore!");
			return false;
		}

		Biome thisBiome = world.getBiome(coord);

		if (biomeMatch(thisBiome, biomes)) {
			return false;
		}

		BlockPos np = mungeFixYcoord(coord);

		if (coord.getY() >= world.getHeight()) {
			OreSpawn.LOGGER.warn("Asked to spawn %s above build limit at %s", oreBlock, coord);
			return false;
		}

		return spawnOrCache(world, np, blockReplace, oreBlock, cacheOverflow, dimension);
	}

	private BlockPos mungeFixYcoord(BlockPos coord) {
		if (coord.getY() < 0) {
			int newYCoord = coord.getY() * -1;
			return new BlockPos(coord.getX(), newYCoord, coord.getZ());
		} else {
			return new BlockPos(coord);
		}
	}

	private boolean spawnOrCache(World world, BlockPos coord, List<IBlockState> blockReplace, IBlockState oreBlock, boolean cacheOverflow, int dimension) {
		if (world.isBlockLoaded(coord)) {
			IBlockState targetBlock = world.getBlockState(coord);

			if (canReplace(targetBlock, blockReplace)) {
				world.setBlockState(coord, oreBlock, 22);
				return true;
			} else {
				return false;
			}
		} else if (cacheOverflow) {
			cacheOverflowBlock(oreBlock, coord, dimension);
			return true;
		}

		return false;
	}

	private void spawnNoCheck(IBlockState oreBlock, World world, BlockPos coord, int dimension,
	    List<IBlockState> blockReplace) {
		if (oreBlock == null) {
			OreSpawn.LOGGER.fatal("FeatureBase.spawn() called with a null ore!");
			return;
		}

		BlockPos np = mungeFixYcoord(coord);

		if (coord.getY() >= world.getHeight()) {
			OreSpawn.LOGGER.warn("Asked to spawn %s above build limit at %s", oreBlock, coord);
			return;
		}

		spawnOrCache(world, np, blockReplace, oreBlock, false, dimension);
	}

	private void cacheOverflowBlock(IBlockState bs, BlockPos coord, int dimension) {
		Vec3i chunkCoord = new Vec3i(coord.getX() >> 4, coord.getY() >> 4, dimension);

		if (overflowCache.containsKey(chunkCoord)) {
			cacheOrder.addLast(chunkCoord);

			if (cacheOrder.size() > MAX_CACHE_SIZE) {
				Vec3i drop = cacheOrder.removeFirst();
				overflowCache.get(drop).clear();
				overflowCache.remove(drop);
			}

			overflowCache.put(chunkCoord, new HashMap<>());
		}

		Map<BlockPos, IBlockState> cache = overflowCache.getOrDefault(chunkCoord, new HashMap<>());
		cache.put(coord, bs);
	}

	private Map<BlockPos, IBlockState> retrieveCache(Vec3i chunkCoord) {
		if (overflowCache.containsKey(chunkCoord)) {
			Map<BlockPos, IBlockState> cache = overflowCache.get(chunkCoord);
			cacheOrder.remove(chunkCoord);
			overflowCache.remove(chunkCoord);
			return cache;
		} else {
			return Collections.emptyMap();
		}
	}

	protected void scramble(int[] target, Random prng) {
		for (int i = target.length - 1; i > 0; i--) {
			int n = prng.nextInt(i);
			int temp = target[i];
			target[i] = target[n];
			target[n] = temp;
		}
	}

	private boolean canReplace(IBlockState target, List<IBlockState> blockToReplace) {
		return !target.getBlock().equals(Blocks.AIR) && blockToReplace.contains(target);
	}

	protected static final Vec3i[] offsets_small = {
		new Vec3i(0, 0, 0), new Vec3i(1, 0, 0),
		new Vec3i(0, 1, 0), new Vec3i(1, 1, 0),

		new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
		new Vec3i(0, 1, 1), new Vec3i(1, 1, 1)
	};

	protected static final Vec3i[] offsets = {
		new Vec3i(-1, -1, -1), new Vec3i(0, -1, -1), new Vec3i(1, -1, -1),
		new Vec3i(-1, 0, -1), new Vec3i(0, 0, -1), new Vec3i(1, 0, -1),
		new Vec3i(-1, 1, -1), new Vec3i(0, 1, -1), new Vec3i(1, 1, -1),

		new Vec3i(-1, -1, 0), new Vec3i(0, -1, 0), new Vec3i(1, -1, 0),
		new Vec3i(-1, 0, 0), new Vec3i(0, 0, 0), new Vec3i(1, 0, 0),
		new Vec3i(-1, 1, 0), new Vec3i(0, 1, 0), new Vec3i(1, 1, 0),

		new Vec3i(-1, -1, 1), new Vec3i(0, -1, 1), new Vec3i(1, -1, 1),
		new Vec3i(-1, 0, 1), new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
		new Vec3i(-1, 1, 1), new Vec3i(0, 1, 1), new Vec3i(1, 1, 1)
	};

	protected static final int[] offsetIndexRef = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
	protected static final int[] offsetIndexRef_small = {0, 1, 2, 3, 4, 5, 6, 7};

	protected static void mergeDefaults(JsonObject parameters, JsonObject defaultParameters) {
		defaultParameters.entrySet().forEach(entry -> {
			if (!parameters.has(entry.getKey()))
				parameters.add(entry.getKey(), entry.getValue());
		});
	}

	private double triangularDistribution(double a, double b, double c) {
		double base = (c - a) / (b - a);
		double rand = this.random.nextDouble();

		if (rand < base) {
			return a + Math.sqrt(rand * (b - a) * (c - a));
		} else {
			return b - Math.sqrt((1 - rand) * (b - a) * (b - c));
		}
	}

	protected int getPoint(int lowerBound, int upperBound, int median) {
		int t = (int)Math.round(triangularDistribution((float)lowerBound, (float)upperBound, (float)median));
		return t - median;
	}


	protected void spawnMunge(FunctionParameterWrapper params, double radius, int count, boolean toPositive) {
		int rSqr = (int)(radius * radius);
		int quantity = count;

		OreList possible = params.getOres();
		World world = params.getWorld();
		BlockPos blockPos = params.getBlockPos();
		List<IBlockState> replace = params.getReplacements();
		BiomeLocation biomes = params.getBiomes();

		for (int dy = (int)(-1 * radius); dy < radius; dy++) {
			for (int dx = getStart(toPositive, radius); endCheck(toPositive, dx, radius); dx = countItem(dx, toPositive)) {
				for (int dz = getStart(toPositive, radius); endCheck(toPositive, dz, radius); dz = countItem(dz, toPositive)) {
					if (getABC(dx, dy, dz) <= rSqr) {
						IBlockState oreBlock = possible.getRandomOre(this.random).getOre();
						spawn(oreBlock, world, blockPos.add(dx, dy, dz), world.provider.getDimension(), true, replace, biomes);

						if (--quantity <= 0) {
							return;
						}
					}
				}
			}
		}
	}

	protected int getABC(int dx, int dy, int dz) {
		return (dx * dx + dy * dy + dz * dz);
	}

	protected int countItem(int dx, boolean toPositive) {
		return toPositive ? dx + 1 : dx - 1;
	}

	protected boolean endCheck(boolean toPositive, int dx, double radius) {
		return toPositive ? (dx >= getStart(toPositive, radius)) : (dx < radius);
	}

	protected int getStart(boolean toPositive, double radius) {
		return ((int)(radius * (toPositive ? 1 : -1)));
	}

	public class FunctionParameterWrapper {
		private World world;
		private BlockPos blockPos;
		private List<IBlockState> replacements;
		private OreList ores;
		private BiomeLocation biomes;
		private ChunkPos chunkPos;
		private IBlockState block;

		public FunctionParameterWrapper() {}

		public FunctionParameterWrapper(FunctionParameterWrapper other) {
			world = other.getWorld();
			blockPos = other.getBlockPos();
			replacements = other.getReplacements();
			ores = other.getOres();
			biomes = other.getBiomes();
			chunkPos = other.getChunkPos();
			block = other.getBlock();
		}

		public BiomeLocation getBiomes() {
			return biomes;
		}

		public void setBiomes(BiomeLocation biomes) {
			this.biomes = biomes;
		}

		public World getWorld() {
			return world;
		}

		public void setWorld(World world) {
			this.world = world;
		}

		public BlockPos getBlockPos() {
			return blockPos;
		}

		public void setBlockPos(BlockPos blockPos) {
			this.blockPos = blockPos;
		}

		public List<IBlockState> getReplacements() {
			return replacements;
		}

		public void setReplacements(List<IBlockState> replacements) {
			this.replacements = replacements;
		}

		public OreList getOres() {
			return ores;
		}

		public void setOres(OreList ores) {
			this.ores = ores;
		}

		public ChunkPos getChunkPos() {
			return chunkPos;
		}

		public void setChunkPos(ChunkPos chunkPos) {
			this.chunkPos = chunkPos;
		}

		public IBlockState getBlock() {
			return block;
		}

		public void setBlock(IBlockState block) {
			this.block = block;
		}
	}
}
