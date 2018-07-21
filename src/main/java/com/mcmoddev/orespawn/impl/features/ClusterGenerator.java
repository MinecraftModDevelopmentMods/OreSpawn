package com.mcmoddev.orespawn.impl.features;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class ClusterGenerator extends FeatureBase implements IFeature {

	private ClusterGenerator(Random rand) {
		super(rand);
	}

	public ClusterGenerator() {
		this(new Random());
	}

	@Override
	public void generate(World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			ISpawnEntry spawnData, ChunkPos _pos) {
		ChunkPos pos = _pos;
		JsonObject params = spawnData.getFeature().getFeatureParameters();

		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;

		mergeDefaults(params, getDefaultParameters());

		runCache(chunkX, chunkZ, world, spawnData);

		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;

		int maxSpread = params.get(Constants.FormatBits.MAX_SPREAD).getAsInt();
		int minHeight = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxHeight = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int variance = params.get(Constants.FormatBits.VARIATION).getAsInt();
		int frequency = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
		int triesMin = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
		int triesMax = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();
		int clusterSize = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();
		int clusterCount = params.get(Constants.FormatBits.NODE_COUNT).getAsInt();

		int tries;

		if (triesMax == triesMin) {
			tries = triesMax;
		} else {
			tries = random.nextInt(triesMax - triesMin) + triesMin;
		}

		while (tries > 0) {
			if (this.random.nextInt(100) <= frequency) {
				int xRand = random.nextInt(16);
				int zRand = random.nextInt(16);

				int x = blockX + xRand - (maxSpread / 2);
				int y = random.nextInt(maxHeight - minHeight) + minHeight;
				int z = blockZ + zRand - (maxSpread / 2);

				spawnCluster(clusterSize, variance, clusterCount, maxSpread, minHeight, maxHeight,
						spawnData, world, new BlockPos(x, y, z));
			}

			tries--;
		}
	}

	private void spawnCluster(int clusterSize, int variance, int clusterCount, int maxSpread,
			int minHeight, int maxHeight, ISpawnEntry spawnData, World world, BlockPos pos) {
		// spawn a cluster at the center, then a bunch around the outside...
		int r = clusterSize - variance;

		if (variance > 0) {
			r += this.random.nextInt(2 * variance) - variance;
		}

		spawnChunk(world, pos, spawnData, r);

		int count = this.random.nextInt(clusterCount - 1); // always at least the first, but vary
															// inside that

		if (variance > 0) {
			count += this.random.nextInt(2 * variance) - variance;
		}

		while (count >= 0) {
			r = clusterSize - variance;

			if (variance > 0) {
				r += this.random.nextInt(2 * variance) - variance;
			}

			int radius = maxSpread / 2;

			int xp = getPoint(-radius, radius, 0);
			int yp = getPoint(minHeight, maxHeight, (maxHeight - minHeight) / 2);
			int zp = getPoint(-radius, radius, 0);

			BlockPos p = pos.add(xp, yp, zp);
			spawnChunk(world, p, spawnData, r);

			count -= r;
		}
	}

	private void spawnChunk(World world, BlockPos pos, ISpawnEntry spawnData, int quantity) {
		int count = quantity;
		int lutType = (quantity < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
		int[] lut = (quantity < 8) ? offsetIndexRef_small : offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];

		System.arraycopy((quantity < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

		int dimension = world.provider.getDimension();

		if (quantity < 27) {
			int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT, this.random);
			int z = 0;

			while (count > 0) {
				IBlockState oreBlock = spawnData.getBlocks().getRandomBlock(random);

				if (!spawn(oreBlock, world, pos.add(offs[scrambledLUT[--count]]), dimension, true,
						spawnData)) {
					count++;
					z++;
				} else {
					z = 0;
				}

				if (z > 5) {
					count--;
					z = 0;
					OreSpawn.LOGGER.warn("Unable to place block for chunk after 5 tries");
				}
			}

			return;
		}

		doSpawnFill(this.random.nextBoolean(), count, spawnData, world, pos);
	}

	private void doSpawnFill(boolean nextBoolean, int quantity, ISpawnEntry spawnData, World world,
			BlockPos pos) {
		int count = quantity;
		double radius = Math.pow(quantity, 1.0 / 3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int) (radius * radius);
		if (nextBoolean) {
			spawnMungeNE(world, pos, rSqr, radius, spawnData, count);
		} else {
			spawnMungeSW(world, pos, rSqr, radius, spawnData, count);
		}
	}

	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MAX_SPREAD, 16);
		defParams.addProperty(Constants.FormatBits.NODE_SIZE, 8);
		defParams.addProperty(Constants.FormatBits.NODE_COUNT, 8);
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 8);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 24);
		defParams.addProperty(Constants.FormatBits.VARIATION, 4);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 25);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MIN, 4);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MAX, 8);
		return defParams;
	}

}
