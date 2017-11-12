package com.mcmoddev.orespawn.impl.features;

import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class VeinGenerator extends FeatureBase implements IFeature {

	private VeinGenerator ( Random rand ) {
		super( rand );
	}
	
	public VeinGenerator() {
		this( new Random() );
	}
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			JsonObject parameters, OreList ores, List<IBlockState> blockReplace, BiomeLocation biomes) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		
		runCache(chunkX, chunkZ, world, blockReplace);
		mergeDefaults(parameters, getDefaultParameters());
		
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;

		int minY = parameters.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxY = parameters.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int vari = parameters.get(Constants.FormatBits.VARIATION).getAsInt();
		int freq = parameters.get(Constants.FormatBits.FREQUENCY).getAsInt();
		int tries = parameters.get(Constants.FormatBits.ATTEMPTS).getAsInt();
		int length = parameters.get(Constants.FormatBits.LENGTH).getAsInt();
		int wander = parameters.get(Constants.FormatBits.WANDER).getAsInt();
		int nodeSize = parameters.get(Constants.FormatBits.NODE_SIZE).getAsInt();

		// we have an offset into the chunk but actually need something more
		while( tries > 0 ) {
			if( this.random.nextInt(100) <= freq ) {
				int x = blockX + random.nextInt(16);
				int y = random.nextInt(maxY - minY) + minY;
				int z = blockZ + random.nextInt(16);

				final int r;
				if(vari > 0){
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}
				spawnVein( new BlockPos(x,y,z), ores, new int[] { length + r, nodeSize, wander }, world, random, blockReplace, biomes);
			}
			tries--;
		}
	}

	// for proper use we need to map these in on the selections later
	// probability of any given vertex-point of a face being selected, each
	// row of this is a row on the face, each float is a point
	private float[][] rowMap = new float[][] {
			{ 0.5f, 0.75f, 0.5f },
			{ 0.5f, 1.00f, 0.5f },
			{ 0.5f, 0.75f, 0.5f }
	};
	
	private float[] colMap = new float[] {
			0.75f, 1.00f, 0.75f
	};
	
	private int triangularDistributionNoRandom( double current ) {
		if( current < 0.5f ) {
			return (int) Math.sqrt( current * 2  );
		} else {
			return (int) (2 - Math.sqrt((1 - current) * 2 ));
		}
	}
	
	private enum EnumFace {
		UP,
		FRONT,
		DOWN,
		BACK,
		LEFT,
		RIGHT;
		
        public static EnumFace getRandomFace(Random random) {
            return values()[random.nextInt(values().length)];
        }

	}
	
	private int[][][][] facePosMap = new int[][][][] {
		{ // top face
			//   x   y   z
			{ { -1,  1,  1 }, {  0,  1,  1 }, {  1,  1,  1 } },  // line vertex 1 to 2
			{ { -1,  1,  0 }, {  0,  1,  0 }, {  1,  0,  1 } },  // center row, no line
			{ { -1,  1, -1 }, {  0,  1, -1 }, {  1,  1, -1 } }   // line vertex 3 to 4			
		},
		{ // front face
			//   x   y   z
			{ { -1,  1,  1 }, {  0,  1,  1 }, {  1,  1,  1 } },  // line vertex 1 to 2
			{ { -1,  0,  1 }, {  0,  0,  1 }, {  1,  0,  1 } },  // center row, no line
			{ { -1, -1,  1 }, {  0, -1,  1 }, {  1, -1,  1 } }   // line vertex 7 to 8
		}, 
		{ // down face
			//   x   y   z
			{ { -1, -1,  1 }, {  0, -1,  1 }, {  1, -1,  1 } },  // line vertex 7 to 8
			{ { -1, -1,  0 }, {  0, -1,  0 }, {  1, -1,  0 } },  // center, no line
			{ { -1, -1, -1 }, {  0, -1, -1 }, {  1, -1, -1 } }   // line vertex 6 to 5
		},
		{ // back face
			//   x   y   z
			{ { -1,  1, -1 }, {  0,  1, -1 }, {  1,  1, -1 } },  // line vertex 3 to 4			
			{ { -1,  0, -1 }, {  0, -1,  0 }, {  1,  0, -1 } },  // center, no line
			{ { -1, -1, -1 }, {  0, -1, -1 }, {  1, -1, -1 } }   // line vertex 6 to 5
		}, 
		{ // left face
			//   x   y   z
			{ {  1,  1,  1 }, {  1,  1,  0 }, {  1,  1, -1 } },  // line vertex 2 to 3
			{ {  1,  0,  1 }, {  1,  0,  0 }, {  1,  0, -1 } },  // line, vertex 2 to 8
			{ {  1, -1,  1 }, {  1, -1,  0 }, {  1, -1, -1 } }   // line, vertex 3 to 5
		}, 
		{ // right face
			//   x   y   z
			{ { -1,  1,  1 }, { -1,  1,  0 }, { -1,  1, -1 } },  // line vertex 2 to 3
			{ { -1,  0,  1 }, { -1,  0,  0 }, { -1,  0, -1 } },  // line, vertex 2 to 8
			{ { -1, -1,  1 }, { -1, -1,  0 }, { -1, -1, -1 } }   // line, vertex 3 to 5
		}
	};
	
	private BlockPos adjustPos(BlockPos pos, int row, int col, EnumFace face) {
		int faceOrd = face.ordinal();
		int[] adjust = facePosMap[faceOrd][row][col];
		return pos.add(adjust[0],adjust[1],adjust[2]);
	}
	
	private enum parms {
		LENGTH, NODESIZE, WANDER
	}
	
	private void spawnVein ( BlockPos blockPos, OreList ores, int[] params, World world, Random random,
	                         List<IBlockState> blockReplace, BiomeLocation biomes ) {
		// passed in POS is our start - we start with a weighting favoring straight directions
		// and three-quarters that to the edges
		// and one-half to the corners
		
		int length = params[parms.LENGTH.ordinal()];
		int nodeSize = params[parms.NODESIZE.ordinal()];
		int wander = params[parms.WANDER.ordinal()];
		
		// generate a node here
		spawnOre(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), blockReplace, nodeSize, biomes);
		// select a direction, decrement length, repeat
		float curRow = 1.00f;
		float curCol = 1.00f;
		int colAdj = 2;
		int rowAdj = 2;
		EnumFace faceToUse = EnumFace.getRandomFace(random);
		int l = length;
		BlockPos workPos = new BlockPos(blockPos);
		
		while ( l > 0 ) {
			workPos = adjustPos(workPos, colAdj, rowAdj, faceToUse);
			
			l--;
			// allow for the "wandering vein" parameter
			if( random.nextInt(100) <= wander ) {
				colAdj = triangularDistributionNoRandom(curCol);
				curCol += colMap[colAdj];
				while( curCol > 1 ) {
					curCol /= 10;
				}
				
				rowAdj = triangularDistributionNoRandom(curCol);
				curRow += rowMap[colAdj][rowAdj];
				while( curRow > 1 ) {
					curRow /= 10;
				}

				spawnOre(ores.getRandomOre(random).getOre(), world, workPos, world.provider.getDimension(), blockReplace, nodeSize, biomes);

				// when nodes are small, the veins get badly broken if we do face wandering
				if( nodeSize > 2 ) {
					faceToUse = EnumFace.getRandomFace(random);
				}
			}
		}
	}

	private void spawnOre ( IBlockState oreBlock, World world, BlockPos key, int dimension, List<IBlockState> blockReplace, int nodeSize, BiomeLocation biomes ) {
		int count = nodeSize;
		int lutType = (count < 8)?offsetIndexRef_small.length:offsetIndexRef.length;
		int[] lut = (count < 8)?offsetIndexRef_small:offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];
		
		System.arraycopy((count < 8)?offsets_small:offsets, 0, offs, 0, lutType);
		
		int[] scrambledLUT = new int[lutType];
		System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
		scramble(scrambledLUT,this.random);
		
		while(count > 0){
			spawn(oreBlock,world,key.add(offs[scrambledLUT[--count]]),dimension,true,blockReplace, biomes );
		}
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 0);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 256);
		defParams.addProperty(Constants.FormatBits.VARIATION, 16);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 50);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS, 8);
		defParams.addProperty(Constants.FormatBits.LENGTH, 16);
		defParams.addProperty(Constants.FormatBits.WANDER, 75);
		defParams.addProperty(Constants.FormatBits.NODE_SIZE, 3);
		return defParams;
	}


	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

}
