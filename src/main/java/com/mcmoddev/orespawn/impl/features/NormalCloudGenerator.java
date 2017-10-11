package com.mcmoddev.orespawn.impl.features;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.util.BinaryTree;

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
			JsonObject parameters, BinaryTree ores, IBlockState blockReplace) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		
		runCache(chunkX, chunkZ, world, blockReplace);
		
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;
		
		int maxSpread  = parameters.get("max-spread").getAsInt();
		int medianSize = parameters.get("median-size").getAsInt();
		int minHeight  = parameters.get("min-height").getAsInt();
		int maxHeight  = parameters.get("max-height").getAsInt();
		int variance   = parameters.get("variance").getAsInt();
		int frequency  = parameters.get("frequency").getAsInt();
		int tries      = parameters.get("tries-per-chunk").getAsInt();
		
		if( frequency <= this.random.nextInt(100) ) {
			while( tries > 0 ) {
				int x = blockX + random.nextInt(16) - (maxSpread / 2);
				int y = random.nextInt(maxHeight - minHeight) + minHeight;
				int z = blockZ + random.nextInt(16) - (maxSpread / 2);
				
				int r = medianSize - variance;
				if(variance > 0){
					r += random.nextInt(2 * variance) - variance;
				}
				
				spawnCloud(ores, new BlockPos(x,y,z), r, maxSpread, minHeight, maxHeight, random, world, blockReplace);
			}
		}
	}

	private double triangularDistribution(double a, double b, double c) {
	    double F = (c - a) / (b - a);
	    double rand = this.random.nextDouble();
	    if (rand < F) {
	        return a + Math.sqrt(rand * (b - a) * (c - a));
	    } else {
	        return b - Math.sqrt((1 - rand) * (b - a) * (b - c));
	    }
	}

	private int getPoint( int lowerBound, int upperBound, int median ) {
		int t = (int)Math.round( triangularDistribution((float)lowerBound, (float)upperBound, (float)median) );
		return t - median;
	}
	
	private void spawnCloud(BinaryTree ores, BlockPos blockPos, int size, int maxSpread, int minHeight, int maxHeight, 
			Random random, World world, IBlockState blockReplace) {
		// spawn one right at the center here, then generate for the cloud and do the math
		spawn(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), true, blockReplace);
		int count = size - 1;
		while( count >= 0 ) {
			BlockPos p = new BlockPos(blockPos);
			p.add( getPoint(minHeight, maxHeight, (maxHeight-minHeight)/2), 
					getPoint(minHeight, maxHeight, (maxHeight-minHeight)/2), 
					getPoint(minHeight, maxHeight, (maxHeight-minHeight)/2) );
			spawn(ores.getRandomOre(random).getOre(), world, p, world.provider.getDimension(), true, blockReplace);
			count--;
		}
	}

	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty("max-spread", 16);
		defParams.addProperty("median-size", 8);
		defParams.addProperty("min-height", 8);
		defParams.addProperty("max-height", 24);
		defParams.addProperty("variance", 4);
		defParams.addProperty("frequency", 25);
		defParams.addProperty("tries-per-chunk", 8);
		return defParams;
	}

}
