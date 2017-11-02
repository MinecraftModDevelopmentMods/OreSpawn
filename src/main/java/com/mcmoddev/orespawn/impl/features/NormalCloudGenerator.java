package com.mcmoddev.orespawn.impl.features;

import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class NormalCloudGenerator extends FeatureBase implements IFeature {

	public NormalCloudGenerator(Random rand) {
		super(rand);
	}

	public NormalCloudGenerator() {
		super( new Random() );
	}
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			JsonObject parameters, OreList ores, List<IBlockState> blockReplace) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		
		mergeDefaults(parameters, getDefaultParameters());

		runCache(chunkX, chunkZ, world, blockReplace);
		
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;

		int maxSpread  = parameters.get(Constants.FormatBits.MAX_SPREAD).getAsInt();
		int medianSize = parameters.get(Constants.FormatBits.MEDIAN_SIZE).getAsInt();
		int minHeight  = parameters.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxHeight  = parameters.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int variance   = parameters.get(Constants.FormatBits.VARIATION).getAsInt();
		int frequency  = parameters.get(Constants.FormatBits.FREQUENCY).getAsInt();
		int tries      = parameters.get(Constants.FormatBits.ATTEMPTS).getAsInt();

		int fSave = frequency;
		while( tries > 0 ) {
			if( this.random.nextInt(100) <= frequency ) {
				int xRand = random.nextInt(16);
				int zRand = random.nextInt(16);
				int mSp = maxSpread;
				
				int x = blockX + xRand - (mSp / 2);
				int y = random.nextInt(maxHeight - minHeight) + minHeight;
				int z = blockZ + zRand - (mSp / 2);

				int r = medianSize - variance;
				if(variance > 0){
					r += random.nextInt(2 * variance) - variance;
				}

				if( !spawnCloud(ores, new BlockPos(x,y,z), new int[] { r, maxSpread, minHeight, maxHeight }, random, world, blockReplace) ) {
					// make another try!
					tries++;
					frequency = 100;
				}
			}
			
			frequency = fSave;
			tries--;
		}
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

	private int getPoint( int lowerBound, int upperBound, int median ) {
		int t = (int)Math.round( triangularDistribution((float)lowerBound, (float)upperBound, (float)median) );
		return t - median;
	}
	
	private enum parms {
		SIZE, MAXSPREAD, MINHEIGHT, MAXHEIGHT;
	}
	
	private boolean spawnCloud(OreList ores, BlockPos blockPos, int[] params, Random random, World world, List<IBlockState> blockReplace) {
		// spawn one right at the center here, then generate for the cloud and do the math
		int size = params[parms.SIZE.ordinal()];
		int maxSpread = params[parms.MAXSPREAD.ordinal()];
		
		if( !spawn(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), true, blockReplace) ) {
			return false;
		}
		
		int count = size;
		
		int radius = maxSpread/2;
		
		while( count > 0 ) {
			int xp = getPoint(-radius, radius, 0);
			int yp = getPoint(-radius, radius, 0);
			int zp = getPoint(-radius, radius, 0);
			
			BlockPos p = blockPos.add( xp, yp, zp );
			
			int z = 0;
			while ( z < 5 && !spawn(ores.getRandomOre(random).getOre(), world, p, world.provider.getDimension(), true, blockReplace) ) {
				xp = getPoint(-radius, radius, 0);
				yp = getPoint(-radius, radius, 0);
				zp = getPoint(-radius, radius, 0);
				
				p = blockPos.add( xp, yp, zp );
				
				z++;
			}
			
			if ( z >= 5 ) {
				OreSpawn.LOGGER.warn("Unable to place block after 5 attempts, cloud will not have selected density");
			}
			count--;
		}
		return true;
	}

	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MAX_SPREAD, 16);
		defParams.addProperty(Constants.FormatBits.MEDIAN_SIZE, 8);
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 8);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 24);
		defParams.addProperty(Constants.FormatBits.VARIATION, 4);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 25);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS, 8);
		return defParams;
	}

}
