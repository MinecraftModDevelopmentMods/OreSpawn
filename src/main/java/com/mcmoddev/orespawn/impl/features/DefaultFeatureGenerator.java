package com.mcmoddev.orespawn.impl.features;

import java.util.Random;

import com.google.gson.JsonObject;
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

public class DefaultFeatureGenerator extends FeatureBase implements IFeature {

    public DefaultFeatureGenerator() {
        super(new Random());
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

        final int minY = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
        final int maxY = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
        final int vari = params.get(Constants.FormatBits.VARIATION).getAsInt();
        final float freq = params.get(Constants.FormatBits.FREQUENCY).getAsFloat();
        final int size = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();

        if (freq >= 1) {
            for (int i = 0; i < freq; i++) {
                final int x = blockX + random.nextInt(16);
                final int y = random.nextInt(maxY - minY) + minY;
                final int z = blockZ + random.nextInt(16);

                final int r;

                if (vari > 0) {
                    r = random.nextInt(2 * vari) - vari;
                } else {
                    r = 0;
                }

                spawnOre(world, spawnData, new BlockPos(x, y, z), size + r);
            }
        } else if (random.nextFloat() < freq) {
            final int x = blockX + random.nextInt(8);
            final int y = random.nextInt(maxY - minY) + minY;
            final int z = blockZ + random.nextInt(8);
            final int r;

            if (vari > 0) {
                r = random.nextInt(2 * vari) - vari;
            } else {
                r = 0;
            }

            spawnOre(world, spawnData, new BlockPos(x, y, z), size + r);
        }

    }

    private void spawnOre(final World world, final ISpawnEntry spawnData, final BlockPos pos,
            final int quantity) {
        int count = quantity;
        final int lutType = (quantity < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
        final int[] lut = (quantity < 8) ? offsetIndexRef_small : offsetIndexRef;
        final Vec3i[] offs = new Vec3i[lutType];

        System.arraycopy((quantity < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

        if (quantity < 27) {
            final int[] scrambledLUT = new int[lutType];
            System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
            scramble(scrambledLUT, this.random);

            while (count > 0) {
                final IBlockState oreBlock = spawnData.getBlocks().getRandomBlock(random);
                final BlockPos target = pos.add(offs[scrambledLUT[--count]]);
                spawn(oreBlock, world, target, world.provider.getDimension(), true, spawnData);
            }

            return;
        }

        doSpawnFill(this.random.nextBoolean(), count, world, spawnData, pos);
    }

    private void doSpawnFill(final boolean nextBoolean, final int quantity, final World world,
            final ISpawnEntry spawnData, final BlockPos pos) {
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
    public JsonObject getDefaultParameters() {
        final JsonObject defParams = new JsonObject();
        defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 0);
        defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 256);
        defParams.addProperty(Constants.FormatBits.VARIATION, 16);
        defParams.addProperty(Constants.FormatBits.FREQUENCY, 0.5);
        defParams.addProperty(Constants.FormatBits.NODE_SIZE, 8);
        return defParams;
    }

    @Override
    public void setRandom(final Random rand) {
        this.random = rand;
    }
}
