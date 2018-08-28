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

    private ClusterGenerator(final Random rand) {
        super(rand);
    }

    public ClusterGenerator() {
        this(new Random());
    }

    @Override
    public void generate(final World world, final IChunkGenerator chunkGenerator,
            final IChunkProvider chunkProvider, final ISpawnEntry spawnData, final ChunkPos _pos) {
        final ChunkPos pos = _pos;
        final JsonObject params = spawnData.getFeature().getFeatureParameters();

        // First, load cached blocks for neighboring chunk ore spawns
        final int chunkX = pos.x;
        final int chunkZ = pos.z;

        mergeDefaults(params, getDefaultParameters());

        runCache(chunkX, chunkZ, world, spawnData);

        // now to ore spawn

        final int blockX = chunkX * 16 + 8;
        final int blockZ = chunkZ * 16 + 8;

        final int maxSpread = params.get(Constants.FormatBits.MAX_SPREAD).getAsInt();
        final int minHeight = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
        final int maxHeight = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
        final int variance = params.get(Constants.FormatBits.VARIATION).getAsInt();
        final int frequency = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
        final int triesMin = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
        final int triesMax = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();
        final int clusterSize = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();
        final int clusterCount = params.get(Constants.FormatBits.NODE_COUNT).getAsInt();

        int tries;

        if (triesMax == triesMin) {
            tries = triesMax;
        } else {
            tries = random.nextInt(triesMax - triesMin) + triesMin;
        }

        while (tries > 0) {
            if (this.random.nextInt(100) <= frequency) {
                final int xRand = random.nextInt(16);
                final int zRand = random.nextInt(16);

                final int x = blockX + xRand - (maxSpread / 2);
                final int y = random.nextInt(maxHeight - minHeight) + minHeight;
                final int z = blockZ + zRand - (maxSpread / 2);

                spawnCluster(clusterSize, variance, clusterCount, maxSpread, minHeight, maxHeight,
                        spawnData, world, new BlockPos(x, y, z));
            }

            tries--;
        }
    }

    private void spawnCluster(final int clusterSize, final int variance, final int clusterCount,
            final int maxSpread, final int minHeight, final int maxHeight,
            final ISpawnEntry spawnData, final World world, final BlockPos pos) {
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

            final int radius = maxSpread / 2;

            final int xp = getPoint(-radius, radius, 0);
            final int yp = getPoint(minHeight, maxHeight, (maxHeight - minHeight) / 2);
            final int zp = getPoint(-radius, radius, 0);

            final BlockPos p = pos.add(xp, yp, zp);
            spawnChunk(world, p, spawnData, r);

            count -= r;
        }
    }

    private void spawnChunk(final World world, final BlockPos pos, final ISpawnEntry spawnData,
            final int quantity) {
        int count = quantity;
        final int lutType = (quantity < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
        final int[] lut = (quantity < 8) ? offsetIndexRef_small : offsetIndexRef;
        final Vec3i[] offs = new Vec3i[lutType];

        System.arraycopy((quantity < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

        final int dimension = world.provider.getDimension();

        if (quantity < 27) {
            final int[] scrambledLUT = new int[lutType];
            System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
            scramble(scrambledLUT, this.random);
            int z = 0;

            while (count > 0) {
                final IBlockState oreBlock = spawnData.getBlocks().getRandomBlock(random);

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

    private void doSpawnFill(final boolean nextBoolean, final int quantity,
            final ISpawnEntry spawnData, final World world, final BlockPos pos) {
        final int count = quantity;
        final double radius = Math.pow(quantity, 1.0 / 3.0) * (3.0 / 4.0 / Math.PI) + 2;
        final int rSqr = (int) (radius * radius);
        if (nextBoolean) {
            spawnMungeNE(world, pos, rSqr, radius, spawnData, count);
        } else {
            spawnMungeSW(world, pos, rSqr, radius, spawnData, count);
        }
    }

    @Override
    public void setRandom(final Random rand) {
        this.random = rand;
    }

    @Override
    public JsonObject getDefaultParameters() {
        final JsonObject defParams = new JsonObject();
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
