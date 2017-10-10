package com.mcmoddev.orespawn.impl.features;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.util.BinaryTree;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class VeinGenerator implements IFeature {
	private static final int MAX_CACHE_SIZE = 1024;
	/** overflow cache so that ores that spawn at edge of chunk can 
	 * appear in the neighboring chunk without triggering a chunk-load */
	private static final Map<Vec3i,Map<BlockPos,IBlockState>> overflowCache = new HashMap<>(MAX_CACHE_SIZE);
	private static final Deque<Vec3i> cacheOrder = new LinkedList<>();

	private Random random;

	public VeinGenerator(Random rand) {
		this.random = rand;
	}
	
	public VeinGenerator() {
		
	}
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			JsonObject parameters, BinaryTree ores, IBlockState blockReplace) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos,IBlockState> cache = retrieveCache(chunkCoord);
		
		if( !cache.isEmpty() ) { // if there is something in the cache, try to spawn it
			for(Entry<BlockPos,IBlockState> ent : cache.entrySet()){
				spawn(cache.get(ent.getKey()),world,ent.getKey(),world.provider.getDimension(),false,blockReplace);
			}
		}
		
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

	private float[][] PointMap = new float[][] {
			{ 0.5f, 0.75f, 0.5f },
			{ 0.5f, 1.00f, 0.5f },
			{ 0.5f, 0.75f, 0.5f }
	};
	
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

	};
	
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
		spawn(ores.findMatchingNode(random.nextInt(ores.getMax())).getOre(), world, blockPos, 
				world.provider.getDimension(), true, blockReplace );
		// select a direction, decrement length, repeat
		int colAdj = random.nextInt(3);
		int rowAdj = random.nextInt(3);
		EnumFace faceToUse = EnumFace.getRandomFace(random);
		while ( length > 0 ) {
			adjustPos(blockPos, colAdj, rowAdj, faceToUse);
			spawn(ores.findMatchingNode(random.nextInt(ores.getMax())).getOre(), world, blockPos, 
					world.provider.getDimension(), true, blockReplace );		
			length--;
			// allow for the "wandering vein" parameter
			if( random.nextInt(100) <= wander ) {
				colAdj = random.nextInt(3);
				rowAdj = random.nextInt(3);
				faceToUse = EnumFace.getRandomFace(random);
			}
		}
	}

	private static void spawnCache(IBlockState b, World w, BlockPos coord, int dimension, boolean cacheOverflow, IBlockState replaceBlock){
		IBlockState b2r = replaceBlock;
		if(b2r == null) {
			b2r = ReplacementsRegistry.getDimensionDefault(w.provider.getDimension());
		}
		if(b2r == null) {
			OreSpawn.LOGGER.fatal("called to spawn %s, replaceBlock is null and the registry says there is no default", b);
			return;
		}
		if(coord.getY() < 0 || coord.getY() >= w.getHeight()) return;
		if(w.isBlockLoaded(coord)){
			IBlockState bs = w.getBlockState(coord);
			if(canReplace(bs,b2r)) {
				w.setBlockState(coord, b, 2);
			}
		} else if(cacheOverflow){
			cacheOverflowBlock(b,coord,dimension);
		}
	}
	
	private static void scramble(int[] target, Random prng) {
		for(int i = target.length - 1; i > 0; i--){
			int n = prng.nextInt(i);
			int temp = target[i];
			target[i] = target[n];
			target[n] = temp;
		}
	}

	private static boolean canReplace(IBlockState target, IBlockState toReplace) {
		if( target.getBlock().equals(Blocks.AIR) ) {
			return false;
		} else if( toReplace.equals(target) ) {
			return true;
		}
		return false;
	}
	
	private void spawn(IBlockState oreBlock, World world, BlockPos key, int dimension, boolean b,
			IBlockState blockReplace) {
		int count = 3;
		int lutType = offsetIndexRef_small.length;
		int[] lut = offsetIndexRef_small;
		Vec3i[] offs = new Vec3i[lutType];
		
		System.arraycopy(offsets_small, 0, offs, 0, lutType);
		int[] scrambledLUT = new int[lutType];
		System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
		scramble(scrambledLUT,this.random);
		while(count > 0){
			spawnCache(oreBlock,world,key.add(offs[scrambledLUT[--count]]),world.provider.getDimension(),true,blockReplace);
		}
		return;
	}

	private static final Vec3i[] offsets_small = {
			new Vec3i( 0, 0, 0),new Vec3i( 1, 0, 0),
			new Vec3i( 0, 1, 0),new Vec3i( 1, 1, 0),

			new Vec3i( 0, 0, 1),new Vec3i( 1, 0, 1),
			new Vec3i( 0, 1, 1),new Vec3i( 1, 1, 1)
	};
	
	private static final int[] offsetIndexRef_small = {0,1,2,3,4,5,6,7};

	protected static void cacheOverflowBlock(IBlockState bs, BlockPos coord, int dimension){
		Vec3i chunkCoord = new Vec3i(coord.getX() >> 4, coord.getY() >> 4, dimension);
		if(overflowCache.containsKey(chunkCoord)){
			cacheOrder.addLast(chunkCoord);
			if(cacheOrder.size() > MAX_CACHE_SIZE){
				Vec3i drop = cacheOrder.removeFirst();
				overflowCache.get(drop).clear();
				overflowCache.remove(drop);
			}
			overflowCache.put(chunkCoord, new HashMap<BlockPos,IBlockState>());
		}
		Map<BlockPos,IBlockState> cache = overflowCache.get(chunkCoord);
		cache.put(coord, bs);
	}

	protected static Map<BlockPos,IBlockState> retrieveCache(Vec3i chunkCoord ){
		if(overflowCache.containsKey(chunkCoord)){
			Map<BlockPos,IBlockState> cache =overflowCache.get(chunkCoord);
			cacheOrder.remove(chunkCoord);
			overflowCache.remove(chunkCoord);
			return cache;
		} else {
			return Collections.<BlockPos,IBlockState>emptyMap();
		}
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
