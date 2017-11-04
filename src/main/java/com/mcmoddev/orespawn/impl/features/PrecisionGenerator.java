package com.mcmoddev.orespawn.impl.features;

import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.Constants.FormatBits;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class PrecisionGenerator extends FeatureBase implements IFeature {
	
	public PrecisionGenerator(Random rand) {
		super(rand);
	}

	public PrecisionGenerator() {
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
		
		// extract parameters
		int nodeCount = parameters.get(FormatBits.NODE_COUNT).getAsInt();
		int maxHeight = parameters.get(FormatBits.MAX_HEIGHT).getAsInt();
		int minHeight = parameters.get(FormatBits.MIN_HEIGHT).getAsInt();
		int nodeSize  = parameters.get(FormatBits.NODE_SIZE).getAsInt();
	
		// now to use them
		int c = nodeCount;
		while( c >= 0 ) {
			BlockPos spot = chooseSpot(chunkX, chunkZ, maxHeight, minHeight);
			spawnAtSpot( spot, nodeSize, maxHeight, minHeight, world, blockReplace, ores);
			c--;
		}
	}

	private void spawnAtSpot(BlockPos spot, int nodeSize, int maxHeight, int minHeight, World world, List<IBlockState> blockReplace,
			OreList ores) {
		int spawned = 0;
		int wanted = nodeSize;
		int counter = nodeSize;
		
		while( counter >= 0 ) {
			int c = spawnOreNode( spot, nodeSize, maxHeight, minHeight, blockReplace, ores, world );
			spawned += c;
			counter -= c;
			if( c == 0 ) {
				OreSpawn.LOGGER.warn("Unable to place node at %s", spot);
				counter = -1;
			}
		}
		
		if( spawned != wanted ) {
			if( spawned > wanted ) {
				OreSpawn.LOGGER.warn("Somehow spawned %d more blocks than wanted for spawn at %s", spawned - wanted, spot);
			} else {
				OreSpawn.LOGGER.warn("Ore Spawn for node at %s is shy %d blocks of requested quantity", spot, wanted - spawned );
			}
		}
	}

	private int spawnOreNode(BlockPos spot, int nodeSize, int maxHeight, int minHeight, List<IBlockState> blockReplace,
			OreList ores, World world ) {
		int count = nodeSize;
		int lutType = (nodeSize < 8)?offsetIndexRef_small.length:offsetIndexRef.length;
		int[] lut = (nodeSize < 8)?offsetIndexRef_small:offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];

		System.arraycopy((nodeSize < 8)?offsets_small:offsets, 0, offs, 0, lutType);
		
		if( nodeSize < 27 ) {
			int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT, this.random);
			
			int nc = 0;
			while(count > 0){
				IBlockState oreBlock = ores.getRandomOre(this.random).getOre();
				if( spawn(oreBlock,world, spot.add(offs[scrambledLUT[--count]]),world.provider.getDimension(),true,blockReplace) ) {
					nc++;
				}
				count--;
			}
			return nc;
		}

		return spawnFill( spot, ores, nodeSize, maxHeight, minHeight, blockReplace, world );
	}

	private int spawnFill(BlockPos spot, OreList ores, int nodeSize, int maxHeight, int minHeight, List<IBlockState> blockReplace,
			World world) {
		int count = nodeSize;
		double radius = Math.pow(nodeSize, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)(radius * radius);
		if( this.random.nextBoolean() ) {
			return spawnMungeNE( world, spot, rSqr, radius, blockReplace, count, ores );
		} else {
			return spawnMungeSW( world, spot, rSqr, radius, blockReplace, count, ores );
		}
	}

	private int spawnMungeSW(World world, BlockPos spot, int rSqr, double radius, List<IBlockState> blockReplace,
			int count, OreList ores) {
		Random prng = this.random;
		int quantity = count;
		int nc = 0;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dx = (int)(radius); dx >= (int)(-1 * radius); dx--){
				for(int dz = (int)(radius); dz >= (int)(-1 * radius); dz--){
					if((dx*dx + dy*dy + dz*dz) <= rSqr) {
						IBlockState oreBlock = ores.getRandomOre(prng).getOre();
						nc += doMungeSpawn(oreBlock,world,spot.add(dx,dy,dz),world.provider.getDimension(),true,blockReplace);
						quantity--;
					}
					
					if(quantity <= 0) {
						return nc;
					}
				}
			}
		}
		return nc;
	}
	
	private int doMungeSpawn(IBlockState oreBlock, World world, BlockPos spot, int dimension, boolean b,
			List<IBlockState> blockReplace) {
		return spawn(oreBlock,world,spot,world.provider.getDimension(),true,blockReplace)?1:0;
	}

	private int spawnMungeNE(World world, BlockPos spot, int rSqr, double radius, List<IBlockState> blockReplace,
			int count, OreList ores) {
		Random prng = this.random;
		int quantity = count;
		int nc = 0;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dz = (int)(-1 * radius); dz < radius; dz++){
				for(int dx = (int)(-1 * radius); dx < radius; dx++){
					if((dx*dx + dy*dy + dz*dz) <= rSqr){
						IBlockState oreBlock = ores.getRandomOre(prng).getOre();
						nc += doMungeSpawn(oreBlock,world,spot.add(dx,dy,dz),world.provider.getDimension(),true,blockReplace);
						quantity--;
					}
					if(quantity <= 0) {
						return nc;
					}
				}
			}
		}
		return nc;
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

	private BlockPos chooseSpot(int xPosition, int zPosition, int maxHeight, int minHeight) {
		int xRet = getPoint( 0, 23, 12 ) + (xPosition * 16);
		int zRet = getPoint( 0, 23, 12 ) + (zPosition * 16);
		int yRange = maxHeight - minHeight;
		int yMod = yRange/2;
		int yRet = getPoint( minHeight, maxHeight, yMod );
		
		return new BlockPos( xRet, yRet, zRet );
	}

	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FormatBits.NODE_COUNT, 4);
		defaults.addProperty(FormatBits.MIN_HEIGHT, 16);
		defaults.addProperty(FormatBits.MAX_HEIGHT, 80);
		defaults.addProperty(FormatBits.NODE_SIZE, 8);
		return defaults;
	}

}
