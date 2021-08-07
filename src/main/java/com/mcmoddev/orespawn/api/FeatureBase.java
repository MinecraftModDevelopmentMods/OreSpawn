package com.mcmoddev.orespawn.api;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.api.os3.OreSpawnBlockMatcher;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class FeatureBase extends IForgeRegistryEntry.Impl<IFeature> {

	private static final int MAX_CACHE_SIZE = 2048;
	/**
	 * overflow cache so that ores that spawn at edge of chunk can appear in the neighboring chunk
	 * without triggering a chunk-load.
	 */
	private static final Map<Vec3i, Map<BlockPos, IBlockState>> overflowCache = new HashMap<>(
			MAX_CACHE_SIZE);
	private static final Deque<Vec3i> cacheOrder = new LinkedList<>();
	protected Random random;

	public FeatureBase(final Random rand) {
		this.random = rand;
	}

	public boolean isValidBlock(final IBlockState oreBlock) {
		return oreBlock.getBlock() != Blocks.AIR;
	}

	protected void runCache(final int chunkX, final int chunkZ, final World world,
			final ISpawnEntry spawnData) {
		final Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());
		final Map<BlockPos, IBlockState> cache = retrieveCache(chunkCoord);

		if (!cache.isEmpty()) { // if there is something in the cache, try to spawn it
			for (final Entry<BlockPos, IBlockState> ent : cache.entrySet()) {
				spawnNoCheck(cache.get(ent.getKey()), world, ent.getKey(),
						world.provider.getDimension(), spawnData);
			}
		}
	}

	protected boolean spawn(final IBlockState oreBlock, final World world, final BlockPos coord,
			final int dimension, final boolean cacheOverflow, final ISpawnEntry spawnData) {
		if (oreBlock == null) {
			OreSpawn.LOGGER.error("FeatureBase.spawn() called with a null ore!");
			return false;
		}

		if (!isValidBlock(oreBlock)) {
			return false;
		}

		final Biome thisBiome = world.getBiome(coord);

		if (!spawnData.biomeAllowed(thisBiome.getRegistryName())) {
			return false;
		}

		final BlockPos np = mungeFixYcoord(coord);

		if (world.isValid(coord)) {
			OreSpawn.LOGGER.warn("Asked to spawn {} outside of world at {}", oreBlock, coord);
			return false;
		}

		return spawnOrCache(world, np, spawnData.getMatcher(), oreBlock, cacheOverflow, dimension,
				spawnData);
	}

	private BlockPos mungeFixYcoord(final BlockPos coord) {
		if (coord.getY() < 0) {
			final int newYCoord = coord.getY() * -1;
			return new BlockPos(coord.getX(), newYCoord, coord.getZ());
		} else {
			return new BlockPos(coord);
		}
	}

	private boolean spawnOrCache(final World world, final BlockPos coord,
			final OreSpawnBlockMatcher replacer, final IBlockState oreBlock,
			final boolean cacheOverflow, final int dimension, final ISpawnEntry spawnData) {
		if (world.isBlockLoaded(coord)) {
			boolean x_bad = false;
			boolean z_bad = false;
			ChunkPos start = world.getChunk(coord).getPos();
			ChunkPos end = new ChunkPos(start.x+1, start.z+1);
			if (coord.getX() < start.getXStart() || coord.getX() > end.getXEnd()) x_bad = true;
			if (coord.getZ() < start.getZStart() || coord.getZ() > end.getZEnd()) z_bad = true;
			
			if(x_bad || z_bad) {
				if(cacheOverflow) {
					cacheOverflowBlock(oreBlock, coord, dimension);
					return true;
				} else {
					return false;
				}
			}
			if (!isValidBlock(oreBlock)) {
				return false;
			}

			final IBlockState targetBlock = world.getBlockState(coord);
			final Biome thisBiome = world.getBiome(coord);

			if (replacer.test(targetBlock) && spawnData.biomeAllowed(thisBiome.getRegistryName())) {
				// don't send block update, send to client, don't re-render, observers don't see the change
				world.setBlockState(coord, oreBlock, 0x16);
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

	private void spawnNoCheck(final IBlockState oreBlock, final World world, final BlockPos coord,
			final int dimension, final ISpawnEntry spawnData) {
		if (oreBlock == null) {
			OreSpawn.LOGGER.error("FeatureBase.spawnNoCheck() called with a null ore!");
			return;
		}

		final BlockPos np = mungeFixYcoord(coord);

		if (coord.getY() >= world.getHeight()) {
			OreSpawn.LOGGER.warn("Asked to spawn {} above build limit at {}", oreBlock, coord);
			return;
		}

		spawnOrCache(world, np, spawnData.getMatcher(), oreBlock, false, dimension, spawnData);
	}

	private void cacheOverflowBlock(final IBlockState bs, final BlockPos coord,
			final int dimension) {
		final Vec3i chunkCoord = new Vec3i(coord.getX() / 16, coord.getY() / 16, dimension);

		if (overflowCache.containsKey(chunkCoord)) {
			cacheOrder.addLast(chunkCoord);

			if (cacheOrder.size() > MAX_CACHE_SIZE) {
				final Vec3i drop = cacheOrder.removeFirst();
				overflowCache.get(drop).clear();
				overflowCache.remove(drop);
			}

			overflowCache.put(chunkCoord, new HashMap<>());
		}

		final Map<BlockPos, IBlockState> cache = overflowCache.getOrDefault(chunkCoord,
				new HashMap<>());
		cache.put(coord, bs);
	}

	private Map<BlockPos, IBlockState> retrieveCache(final Vec3i chunkCoord) {
		if (overflowCache.containsKey(chunkCoord)) {
			final Map<BlockPos, IBlockState> cache = overflowCache.get(chunkCoord);
			cacheOrder.remove(chunkCoord);
			overflowCache.remove(chunkCoord);
			return cache;
		} else {
			return Collections.emptyMap();
		}
	}

	protected void scramble(final int[] target, final Random prng) {
		for (int i = target.length - 1; i > 0; i--) {
			final int n = prng.nextInt(i);
			final int temp = target[i];
			target[i] = target[n];
			target[n] = temp;
		}
	}

	protected static final Vec3i[] offsets_small = {
			new Vec3i(0, 0, 0), new Vec3i(1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(1, 1, 0),

			new Vec3i(0, 0, 1), new Vec3i(1, 0, 1), new Vec3i(0, 1, 1), new Vec3i(1, 1, 1)
	};

	protected static final Vec3i[] offsets = {
			new Vec3i(-1, -1, -1), new Vec3i(0, -1, -1), new Vec3i(1, -1, -1), new Vec3i(-1, 0, -1),
			new Vec3i(0, 0, -1), new Vec3i(1, 0, -1), new Vec3i(-1, 1, -1), new Vec3i(0, 1, -1),
			new Vec3i(1, 1, -1),

			new Vec3i(-1, -1, 0), new Vec3i(0, -1, 0), new Vec3i(1, -1, 0), new Vec3i(-1, 0, 0),
			new Vec3i(0, 0, 0), new Vec3i(1, 0, 0), new Vec3i(-1, 1, 0), new Vec3i(0, 1, 0),
			new Vec3i(1, 1, 0),

			new Vec3i(-1, -1, 1), new Vec3i(0, -1, 1), new Vec3i(1, -1, 1), new Vec3i(-1, 0, 1),
			new Vec3i(0, 0, 1), new Vec3i(1, 0, 1), new Vec3i(-1, 1, 1), new Vec3i(0, 1, 1),
			new Vec3i(1, 1, 1)
	};

	protected static final int[] offsetIndexRef = {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
			24, 25, 26
	};
	protected static final int[] offsetIndexRef_small = {
			0, 1, 2, 3, 4, 5, 6, 7
	};

	protected static void mergeDefaults(final JsonObject parameters,
			final JsonObject defaultParameters) {
		defaultParameters.entrySet().forEach(entry -> {
			if (!parameters.has(entry.getKey())) {
				parameters.add(entry.getKey(), entry.getValue());
			}
		});
	}

	private double triangularDistribution(final double a, final double b, final double c) {
		final double base = (c - a) / (b - a);
		final double rand = this.random.nextDouble();

		if (rand < base) {
			return a + Math.sqrt(rand * (b - a) * (c - a));
		} else {
			return b - Math.sqrt((1 - rand) * (b - a) * (b - c));
		}
	}

	protected int getPoint(final int lowerBound, final int upperBound, final int median) {
		final int t = (int) Math.round(
				triangularDistribution((float) lowerBound, (float) upperBound, (float) median));
		return t - median;
	}

	protected void spawnMungeInner(final Random prng, final int rSqr, int quantity,
			final Vec3i vals, final ISpawnEntry spawnData, final World world, final BlockPos blockPos) {
		int dx = vals.getX();
		int dy = vals.getY();
		int dz = vals.getZ();
		final IBlockList possibleOres = spawnData.getBlocks();
		final OreSpawnBlockMatcher replacer = spawnData.getMatcher();
		
		if ((dx * dx + dy * dy + dz * dz) <= rSqr) {
			final IBlockState oreBlock = possibleOres.getRandomBlock(prng);
			if (oreBlock.getBlock().equals(net.minecraft.init.Blocks.AIR)) return;
			spawnOrCache(world, blockPos.add(dx, dy, dz), replacer, oreBlock, true,
					world.provider.getDimension(), spawnData);
			quantity--;
		}
	}
	
	protected void spawnMungeSW(final World world, final BlockPos blockPos, final int rSqr,
			final double radius, final ISpawnEntry spawnData, final int count) {
		final Random prng = this.random;
		int quantity = count;
		for (int dy = (int) (-1 * radius); dy < radius; dy++) {
			for (int dx = (int) (radius); dx >= (int) (-1 * radius); dx--) {
				for (int dz = (int) (radius); dz >= (int) (-1 * radius); dz--) {
					spawnMungeInner(prng, rSqr, quantity, new Vec3i(dx,dy,dz), spawnData, world, blockPos);
					if (quantity <= 0) {
						return;
					}
				}
			}
		}
	}

	protected void spawnMungeNE(final World world, final BlockPos blockPos, final int rSqr,
			final double radius, final ISpawnEntry spawnData, final int count) {
		final Random prng = this.random;
		int quantity = count;
//		final IBlockList possibleOres = spawnData.getBlocks();
//		final OreSpawnBlockMatcher replacer = spawnData.getMatcher();
		for (int dy = (int) (-1 * radius); dy < radius; dy++) {
			for (int dz = (int) (-1 * radius); dz < radius; dz++) {
				for (int dx = (int) (-1 * radius); dx < radius; dx++) {
					spawnMungeInner(prng, rSqr, quantity, new Vec3i(dx,dy,dz), spawnData, world, blockPos);
					if (quantity <= 0) {
						return;
					}
				}
			}
		}
	}

	protected int getABC(final int dx, final int dy, final int dz) {
		return (dx * dx + dy * dy + dz * dz);
	}

	protected int countItem(final int dx, final boolean toPositive) {
		return toPositive ? dx + 1 : dx - 1;
	}

	protected boolean endCheck(final boolean toPositive, final int dx, final double radius) {
		return toPositive ? (dx >= getStart(toPositive, radius)) : (dx < radius);
	}

	protected int getStart(final boolean toPositive, final double radius) {
		return ((int) (radius * (toPositive ? 1 : -1)));
	}
}
