package com.mcmoddev.orespawn.impl.features;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.GeneratorParameters;
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

	private NormalCloudGenerator ( Random rand ) {
		super(rand);
	}

	public NormalCloudGenerator() {
		this( new Random() );
	}

	@Override
	public void generate( World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
	                      GeneratorParameters parameters ) {
		ChunkPos pos = parameters.getChunk();
		List<IBlockState> blockReplace = new LinkedList<>();
		blockReplace.addAll( parameters.getReplacements() );
		JsonObject params = parameters.getParameters();
		OreList ores = parameters.getOres();
		BiomeLocation biomes = parameters.getBiomes();
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		
		mergeDefaults(params, getDefaultParameters());

		runCache(chunkX, chunkZ, world, blockReplace);
		
		// now to ore spawn

		// lets not offset blind, 
		int blockX = chunkX * 16;
		int blockZ = chunkZ * 16;

		int maxSpread  = params.get(Constants.FormatBits.MAX_SPREAD).getAsInt();
		int medianSize = params.get(Constants.FormatBits.MEDIAN_SIZE).getAsInt();
		int minHeight  = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxHeight  = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int variance   = params.get(Constants.FormatBits.VARIATION).getAsInt();
		int frequency  = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
		int tries      = params.get(Constants.FormatBits.ATTEMPTS).getAsInt();

		// on the X and Z you have a possible 2-chunk range - 32 blocks - subtract the spread to get
		// a size that will let us insert by the radius
		int offsetXZ = 32 - maxSpread;
		
		// you have the distance between minHeight and maxHeight
		// this is the actual size of the space
		int sizeY = (maxHeight - minHeight);
		int offsetY = sizeY - maxSpread;
		int radiusXZ = offsetXZ / 2;
		
		// actual radius for placement is the size minus the spread to center it in the space and keep
		// from overflowing
		int radiusY = offsetY/2;

		// we center at the minimum plus the half the height
		int blockY = minHeight + (sizeY/2);
		
		int fSave = frequency;
		int tryCount = 0;

		while( tries > 0 ) {
			if( this.random.nextInt(100) <= frequency ) {
				frequency = fSave;
				int x = blockX + getPoint(0, offsetXZ, radiusXZ) + radiusXZ;
				// this should, hopefully, keep us centered between minHeight and maxHeight with nothing going above/below those values
				int y = blockY + getPoint(0, offsetY, radiusY);
				int z = blockZ + getPoint(0, offsetXZ, radiusXZ) + radiusXZ;
				
				int r = medianSize - variance;
				if(variance > 0){
					r += random.nextInt(2 * variance) - variance;
				}

				if( !spawnCloud(ores, new BlockPos(x,y,z), new int[] { r, maxSpread, minHeight, maxHeight }, random, world, blockReplace, biomes) &&
						tryCount < 5 ) {
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

	private enum parms {
		SIZE, MAXSPREAD, MINHEIGHT, MAXHEIGHT
	}
	
	private boolean spawnCloud ( OreList ores, BlockPos blockPos, int[] params, Random random, World world, List<IBlockState> blockReplace, BiomeLocation biomes ) {
		// spawn one right at the center here, then generate for the cloud and do the math
		int size = params[parms.SIZE.ordinal()];
		int maxSpread = params[parms.MAXSPREAD.ordinal()];
		
		if( !spawn(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), true, blockReplace, biomes ) ) {
			return false;
		}
		
		int radius = maxSpread/2;
		boolean alreadySpewed = false;
		int count = Math.min( size, (int)Math.round( Math.PI * Math.pow(radius, 2) ) );
		
		while( count > 0 ) {
			int xp = getPoint(0, maxSpread, radius);
			int yp = getPoint(params[parms.MINHEIGHT.ordinal()], params[parms.MAXHEIGHT.ordinal()], (params[parms.MAXHEIGHT.ordinal()] - params[parms.MINHEIGHT.ordinal()])/2);
			int zp = getPoint(0, maxSpread, radius);
			
			BlockPos p = blockPos.add( xp, yp, zp );
			
			int z = 0;
			while ( z < 5 && !spawn(ores.getRandomOre(random).getOre(), world, p, world.provider.getDimension(), true, blockReplace, biomes ) ) {
				xp = getPoint(0, maxSpread, radius);
				yp = getPoint(params[parms.MINHEIGHT.ordinal()], params[parms.MAXHEIGHT.ordinal()], (params[parms.MAXHEIGHT.ordinal()] - params[parms.MINHEIGHT.ordinal()])/2);
				zp = getPoint(0, maxSpread, radius);
				
				p = blockPos.add( xp, yp, zp );
				
				z++;
			}
			
			if( z >= 5 && !alreadySpewed ) {
				OreSpawn.LOGGER.info("unable to achieve requested cloud density for cloud centered at %s", blockPos);
				alreadySpewed = true;
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
