package com.mcmoddev.orespawn.impl.features;

import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.util.BinaryTree;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class ClusterGenerator extends FeatureBase implements IFeature {

	public ClusterGenerator(Random rand) {
		super(rand);
	}

	public ClusterGenerator() {
		super( new Random() );
	}
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			JsonObject parameters, BinaryTree ores, List<IBlockState> blockReplace) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;

		mergeDefaults(parameters, getDefaultParameters());

		runCache(chunkX, chunkZ, world, blockReplace);
		
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;
		
		int maxSpread    = parameters.get("max-spread").getAsInt();
		int clusterSize  = parameters.get("cluster-size").getAsInt();
		int clusterCount = parameters.get("cluster-count").getAsInt();
		int minHeight    = parameters.get("min-height").getAsInt();
		int maxHeight    = parameters.get("max-height").getAsInt();
		int variance     = parameters.get("variance").getAsInt();
		int frequency    = parameters.get("frequency").getAsInt();
		int tries        = parameters.get("tries-per-chunk").getAsInt();
		
		while( tries > 0 ) {
			if( this.random.nextInt(100) <= frequency ) {
				int x = blockX + random.nextInt(16) - (maxSpread / 2);
				int y = random.nextInt(maxHeight - minHeight) + minHeight;
				int z = blockZ + random.nextInt(16) - (maxSpread / 2);
				int[] params = new int[] { clusterSize, variance, clusterCount, maxSpread, minHeight, maxHeight};

				spawnCluster(ores, new BlockPos(x,y,z), params, random, world, blockReplace);
			}
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
		SIZE, VARIANCE, CCOUNT, MAXSPREAD, MINHEIGHT, MAXHEIGHT;
	}
	
	private void spawnCluster(BinaryTree ores, BlockPos blockPos, int[] params, Random random, World world, List<IBlockState> blockReplace) {
		int size = params[parms.SIZE.ordinal()];
		int variance = params[parms.VARIANCE.ordinal()];
		int clusterCount = params[parms.CCOUNT.ordinal()];
		int maxSpread = params[parms.MAXSPREAD.ordinal()];
		int minHeight = params[parms.MINHEIGHT.ordinal()];
		int maxHeight = params[parms.MAXHEIGHT.ordinal()];
		// spawn a cluster at the center, then a bunch around the outside...
		int r = size - variance;
		if(variance > 0){
			r += random.nextInt(2 * variance) - variance;
		}
		
		spawnChunk(ores, world, blockPos, r, world.provider.getDimension(), blockReplace, random);
		int count = random.nextInt(clusterCount - 1); // always at least the first, but vary inside that
		if( variance > 0) {
			count += random.nextInt(2 * variance) - variance;
		}
		
		while( count >= 0 ) {
			r = size - variance;
			if(variance > 0){
				r += random.nextInt(2 * variance) - variance;
			}
			
			BlockPos p = new BlockPos(blockPos);
			p.add( getPoint(minHeight, maxHeight, maxSpread/2), 
					getPoint(minHeight, maxHeight, maxSpread/2), 
					getPoint(minHeight, maxHeight, maxSpread/2) );
			spawnChunk(ores, world, p, r, world.provider.getDimension(), blockReplace, random);
			count -= r;
		}
	}

	private void spawnChunk(BinaryTree ores, World world, BlockPos blockPos, int quantity, int dimension, List<IBlockState> blockReplace,
			Random prng) {
		int count = quantity;
		int lutType = (quantity < 8)?offsetIndexRef_small.length:offsetIndexRef.length;
		int[] lut = (quantity < 8)?offsetIndexRef_small:offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];
		
		System.arraycopy((quantity < 8)?offsets_small:offsets, 0, offs, 0, lutType);
		
		if( quantity < 27 ) {
			int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				IBlockState oreBlock = ores.getRandomOre(prng).getOre();
				spawn(oreBlock,world,blockPos.add(offs[scrambledLUT[--count]]),dimension,true,blockReplace);
			}
			return;
		}
		
		doSpawnFill( prng.nextBoolean(), world, blockPos, count, blockReplace, ores );
		
		return;		
	}

	private void doSpawnFill(boolean nextBoolean, World world, BlockPos blockPos, int quantity, List<IBlockState> blockReplace, BinaryTree possibleOres ) {
		int count = quantity;
		double radius = Math.pow(quantity, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)(radius * radius);
		if( nextBoolean ) {
			spawnMungeNE( world, blockPos, rSqr, radius, blockReplace, count, possibleOres );
		} else {
			spawnMungeSW( world, blockPos, rSqr, radius, blockReplace, count, possibleOres );
		}
	}


	private void spawnMungeSW(World world, BlockPos blockPos, int rSqr, double radius,
			List<IBlockState> blockReplace, int count, BinaryTree possibleOres ) {
		Random prng = this.random;
		int quantity = count;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dx = (int)(radius); dx >= (int)(-1 * radius); dx--){
				for(int dz = (int)(radius); dz >= (int)(-1 * radius); dz--){
					if((dx*dx + dy*dy + dz*dz) <= rSqr){
						IBlockState oreBlock = possibleOres.getRandomOre(prng).getOre();
						spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,blockReplace);
						quantity--;
					}
					if(quantity <= 0) {
						return;
					}
				}
			}
		}
	}


	private void spawnMungeNE(World world, BlockPos blockPos, int rSqr, double radius,
			List<IBlockState> blockReplace, int count, BinaryTree possibleOres) {
		Random prng = this.random;
		int quantity = count;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dz = (int)(-1 * radius); dz < radius; dz++){
				for(int dx = (int)(-1 * radius); dx < radius; dx++){
					if((dx*dx + dy*dy + dz*dz) <= rSqr){
						IBlockState oreBlock = possibleOres.getRandomOre(prng).getOre();
						spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,blockReplace);
						quantity--;
					}
					if(quantity <= 0) {
						return;
					}
				}
			}
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
		defParams.addProperty("cluster-size", 8);
		defParams.addProperty("cluster-count", 8);
		defParams.addProperty("min-height", 8);
		defParams.addProperty("max-height", 24);
		defParams.addProperty("variance", 4);
		defParams.addProperty("frequency", 25);
		defParams.addProperty("tries-per-chunk", 8);
		return defParams;
	}

}
