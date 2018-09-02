/**
 * 
 */
package com.mcmoddev.orespawn.impl.features;

import java.util.Random;
import java.util.Set;
import java.util.stream.StreamSupport;

import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidUtil;


/**
 * @author Daniel Hazelton
 *
 */
public class UnderFluid extends FeatureBase implements IFeature {

	/**
	 * @param rand
	 */
	public UnderFluid(Random rand) {
		super(rand);
	}

	/** 
	 * Try to generate this feature to the specified parameters in the specified chunk.
	 * @see com.mcmoddev.orespawn.api.IFeature#generate(net.minecraft.world.World, net.minecraft.world.gen.IChunkGenerator, net.minecraft.world.chunk.IChunkProvider, com.mcmoddev.orespawn.api.os3.ISpawnEntry, net.minecraft.util.math.ChunkPos)
	 * <p>Theory:
	 * <p style="indent:4em;">For up to 2*maximum_tries pick a random spot in the spawn zone do:
	 * <p style="indent:8em;">Check the 5x5x5 region around that spot for the fluid:
	 * <p style="indent:10em;">if found, store the BlockPos of the fluid, if this store has reached maximum_tries entries, stop iteration
	 * <p style="indent:4em;">For each of the found positions, if any, seek up and down from the position to find the lower bound of the fluid
	 * <p style="indent:4em;">At the fluids lower bound, generate the node.
	 * 
	 * @param world {@link net.minecraft.world.World} World this chunk is in
	 * @param chunkGenerator {@link net.minecraft.world.gen.IChunkGenerator} Chunk generator for this chunk
	 * @param chunkProvider {@link net.minecraft.world.chunk.IChunkGenerator} Chunk provider for this chunk
	 * @param spawnData {@link com.mcmoddev.orespawn.api.os3.ISpawnEntry} Parameters and data on this spawn from the configuration file
	 * @param _pos {@link net.minecraft.util.math.ChunkPos} Absolute in-world coordinates, on the chunk grid, for this chunk (can get lowest value X/Z block coordinates for this chunk by multiplying provided X/Z by 16)
	 */
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

		
		final int minHeight = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		final int maxHeight = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		final int variance = params.get(Constants.FormatBits.VARIATION).getAsInt();
		final int triesMin = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
		final int triesMax = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();
		final int nodeSize = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();
		final Fluid fluid = FluidRegistry.getFluid(params.get(Constants.FormatBits.FLUID).getAsString());
		
		int tries = this.random.nextInt(triesMax - triesMin + 1) + triesMin;
		int surveySize = triesMax * 2;
		int ySpan = maxHeight - minHeight;
		BlockPos refBlock = new BlockPos(blockX, minHeight, blockZ);
		Block fluidBlock = fluid.getBlock();
		
		Set<BlockPos> found = Sets.newHashSet();
		
		for (int j = 0; j < surveySize; j++) {
			BlockPos sampleCenter = refBlock.add(this.random.nextInt(16), this.random.nextInt(ySpan), this.random.nextInt(16));
			BlockPos lowSide = sampleCenter.add(-2,-2,-2);
			BlockPos highSide = sampleCenter.add(2,2,2);
			StreamSupport.stream(BlockPos.getAllInBoxMutable(lowSide, highSide).spliterator(), false)
			.filter( bp -> world.getBlockState(bp).getMaterial().isLiquid() &&
					world.getBlockState(bp).getBlock().equals(fluidBlock) &&
					!found.contains(bp) && bp.getY() >= minHeight )
			.map(BlockPos.MutableBlockPos::toImmutable)
			.forEach(found::add);
		}

		for(int i = 0; i < tries; i++) {
			BlockPos mp = found.toArray(new BlockPos[found.size()])[this.random.nextInt(found.size())];
			found.remove(mp);
			while(world.getBlockState(mp).getMaterial().isLiquid() && mp.getY() >= minHeight) {
				mp = mp.add(0, -1, 0);
			}
			
			// calculate actual size for this node
			int size = this.random.nextInt(nodeSize - variance + 1) + this.random.nextInt(variance);
			
			// try to spawn now, as we do ***NOT*** want the node to wrap, we have to do this different...
			// so... we copy vanilla spawn logic, to a degree, I think
			spawnOre(world, spawnData, mp, size);
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

	/* (non-Javadoc)
	 * @see com.mcmoddev.orespawn.api.IFeature#setRandom(java.util.Random)
	 */
	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	/* (non-Javadoc)
	 * @see com.mcmoddev.orespawn.api.IFeature#getDefaultParameters()
	 */
	@Override
	public JsonObject getDefaultParameters() {
		final JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 0);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 256);
		defParams.addProperty(Constants.FormatBits.VARIATION, 16);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MIN, 4);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MAX, 4);
		defParams.addProperty(Constants.FormatBits.NODE_SIZE, 8);
		defParams.addProperty(Constants.FormatBits.FLUID, "water");
		return defParams;
	}

}
