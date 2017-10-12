package com.mcmoddev.orespawn.impl.features;

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

public class VeinGenerator extends FeatureBase implements IFeature {

	public VeinGenerator(Random rand) {
		super( rand );
	}
	
	public VeinGenerator() {
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
		
		int minY = parameters.get("minHeight").getAsInt();
		int maxY = parameters.get("maxHeight").getAsInt();
		int vari = parameters.get("variation").getAsInt();
		int freq = parameters.get("frequency").getAsInt();
		int tries = parameters.get("attempts").getAsInt();
		int length = parameters.get("length").getAsInt();
		int wander = parameters.get("wander").getAsInt();
		int nodeSize = parameters.get("node-size").getAsInt();
		
		// we have an offset into the chunk but actually need something more
		if( freq <= this.random.nextInt(100) ) {
			while( tries > 0 ) {
				int x = blockX + random.nextInt(8);
				int y = random.nextInt(maxY - minY) + minY;
				int z = blockZ + random.nextInt(8);
				
				final int r;
				if(vari > 0){
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}
				spawnVein( new BlockPos(x,y,z), ores, length + r, nodeSize, wander, world, random, blockReplace);
			}
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
	
	private void adjustPos(BlockPos pos, int row, int col, EnumFace face) {
		int faceOrd = face.ordinal() - 1;
		int[] adjust = facePosMap[faceOrd][row][col];
		pos.add(adjust[0],adjust[1],adjust[2]);
	}
	
	private void spawnVein(BlockPos blockPos, BinaryTree ores, int length, int nodeSize, int wander, World world, Random random,
			IBlockState blockReplace) {		
		// passed in POS is our start - we start with a weighting favoring straight directions
		// and three-quarters that to the edges
		// and one-half to the corners
		
		// generate a node here
		spawn(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), true, blockReplace );
		// select a direction, decrement length, repeat
		float curRow = 1.00f;
		float curCol = 1.00f;
		int colAdj = 2;
		int rowAdj = 2;
		EnumFace faceToUse = EnumFace.getRandomFace(random);
		int l = length;
		while ( l > 0 ) {
			adjustPos(blockPos, colAdj, rowAdj, faceToUse);
			spawnOre(ores.getRandomOre(random).getOre(), world, blockPos, world.provider.getDimension(), blockReplace, nodeSize );		
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

				faceToUse = EnumFace.getRandomFace(random);
			}
		}
	}

	private void spawnOre(IBlockState oreBlock, World world, BlockPos key, int dimension, IBlockState blockReplace, int nodeSize) {
		int count = nodeSize;
		int lutType = offsetIndexRef_small.length;
		int[] lut = offsetIndexRef_small;
		Vec3i[] offs = new Vec3i[lutType];
		
		System.arraycopy(offsets_small, 0, offs, 0, lutType);
		int[] scrambledLUT = new int[lutType];
		System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
		scramble(scrambledLUT,this.random);
		while(count > 0){
			spawn(oreBlock,world,key.add(offs[scrambledLUT[--count]]),world.provider.getDimension(),true,blockReplace);
		}
		return;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty("minHeight", 0);
		defParams.addProperty("maxHeight", 256);
		defParams.addProperty("variation", 16);
		defParams.addProperty("frequency", 50); // in this, frequency is an int
		defParams.addProperty("length", 16);
		defParams.addProperty("node-size", 3);
		defParams.addProperty("wander", 75); // how much this can wander
		defParams.addProperty("attempts", 8); // how often to attempt to make a vein
		return defParams;
	}


	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

}
