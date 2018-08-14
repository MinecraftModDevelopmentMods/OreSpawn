package com.mcmoddev.orespawn.impl.features;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class NormalCloudGenerator extends FeatureBase implements IFeature {

	private NormalCloudGenerator(final Random rand) {
		super(rand);
	}

	public NormalCloudGenerator() {
		this(new Random());
	}

	@Override
	public void generate(final World world, final IChunkGenerator chunkGenerator, final IChunkProvider chunkProvider,
			final ISpawnEntry spawnData, final ChunkPos _pos) {
		final ChunkPos pos = _pos;
		final JsonObject params = spawnData.getFeature().getFeatureParameters();

		// First, load cached blocks for neighboring chunk ore spawns
		final int chunkX = pos.x;
		final int chunkZ = pos.z;

		mergeDefaults(params, getDefaultParameters());

		runCache(chunkX, chunkZ, world, spawnData);

		// now to ore spawn

		// lets not offset blind,
		final int blockX = chunkX * 16;
		final int blockZ = chunkZ * 16;

		final int maxSpread = params.get(Constants.FormatBits.MAX_SPREAD).getAsInt();
		final int medianSize = params.get(Constants.FormatBits.MEDIAN_SIZE).getAsInt();
		final int minHeight = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		final int maxHeight = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		final int variance = params.get(Constants.FormatBits.VARIATION).getAsInt();
		int frequency = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
		final int triesMin = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
		final int triesMax = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();

		// on the X and Z you have a possible 2-chunk range - 32 blocks - subtract the spread to get
		// a size that will let us insert by the radius
		final int offsetXZ = 32 - maxSpread;

		// you have the distance between minHeight and maxHeight
		// this is the actual size of the space
		final int sizeY = (maxHeight - minHeight);
		final int offsetY = sizeY - maxSpread;
		final int radiusXZ = offsetXZ / 2;

		// actual radius for placement is the size minus the spread to center it in the space and
		// keep
		// from overflowing
		final int radiusY = offsetY / 2;

		// we center at the minimum plus the half the height
		final int blockY = minHeight + (sizeY / 2);

		final int fSave = frequency;
		int tryCount = 0;

		int tries;

		if (triesMax == triesMin) {
			tries = triesMax;
		} else {
			tries = random.nextInt(triesMax - triesMin) + triesMin;
		}

		while (tries > 0) {
			if (this.random.nextInt(100) <= frequency) {
				frequency = fSave;
				final int x = blockX + getPoint(0, offsetXZ, radiusXZ) + radiusXZ;
				// this should, hopefully, keep us centered between minHeight and maxHeight with
				// nothing going above/below those values
				final int y = blockY + getPoint(0, offsetY, radiusY);
				final int z = blockZ + getPoint(0, offsetXZ, radiusXZ) + radiusXZ;

				int r = medianSize - variance;

				if (variance > 0) {
					r += random.nextInt(2 * variance) - variance;
				}

				final BlockPos p = new BlockPos(x, y, z);

				if (!spawnCloud(r, maxSpread, minHeight, maxHeight, p, spawnData, world)
						&& tryCount < 5) {
					// make another try!
					tries++;
					frequency = 100;
					tryCount++;
				} else {
					tryCount = 0;
				}
			}

			tries--;
		}
	}

	private boolean spawnCloud(final int size, final int maxSpread, final int minHeight, final int maxHeight, final BlockPos pos,
			final ISpawnEntry spawnData, final World world) {
		// spawn one right at the center here, then generate for the cloud and do the math

		if (!spawn(spawnData.getBlocks().getRandomBlock(random), world, pos,
				world.provider.getDimension(), true, spawnData)) {
			return false;
		}

		final int radius = maxSpread / 2;
		boolean alreadySpewed = false;
		int count = Math.min(size, (int) Math.round(Math.PI * Math.pow(radius, 2)));

		while (count > 0) {
			int xp = getPoint(0, maxSpread, radius);
			int yp = getPoint(minHeight, maxHeight, (maxHeight - minHeight) / 2);
			int zp = getPoint(0, maxSpread, radius);

			BlockPos p = pos.add(xp, yp, zp);

			int z = 0;

			while (z < 5 && !spawn(spawnData.getBlocks().getRandomBlock(random), world, p,
					world.provider.getDimension(), true, spawnData)) {
				xp = getPoint(0, maxSpread, radius);
				yp = getPoint(minHeight, maxHeight, (maxHeight - minHeight) / 2);
				zp = getPoint(0, maxSpread, radius);

				p = pos.add(xp, yp, zp);

				z++;
			}

			if (z >= 5 && !alreadySpewed) {
				OreSpawn.LOGGER.info(
						"unable to achieve requested cloud density for cloud centered at %s", pos);
				alreadySpewed = true;
			}

			count--;
		}

		return true;
	}

	@Override
	public void setRandom(final Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		final JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MAX_SPREAD, 16);
		defParams.addProperty(Constants.FormatBits.MEDIAN_SIZE, 8);
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 8);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 24);
		defParams.addProperty(Constants.FormatBits.VARIATION, 4);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 25);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MIN, 4);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MAX, 4);
		return defParams;
	}
}
