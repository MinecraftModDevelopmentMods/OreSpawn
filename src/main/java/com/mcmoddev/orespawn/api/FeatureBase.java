package com.mcmoddev.orespawn.api;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

public class FeatureBase {
	private static final int MAX_CACHE_SIZE = 2048;
	/** overflow cache so that ores that spawn at edge of chunk can 
	 * appear in the neighboring chunk without triggering a chunk-load */
	protected static final Map<Vec3i,Map<BlockPos,IBlockState>> overflowCache = new HashMap<>(MAX_CACHE_SIZE);
	protected static final Deque<Vec3i> cacheOrder = new LinkedList<>();
	protected Random random;
	
	public FeatureBase( Random rand ) {
		this.random = rand;
	}
	
	protected void runCache(int chunkX, int chunkZ, World world, List<IBlockState> blockReplace) {
		Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos,IBlockState> cache = retrieveCache(chunkCoord);
		
		if( !cache.isEmpty() ) { // if there is something in the cache, try to spawn it
			for(Entry<BlockPos,IBlockState> ent : cache.entrySet()){
				spawn( cache.get(ent.getKey()), world, ent.getKey(), world.provider.getDimension(), false, blockReplace);
			}
		}
	}
	
	protected boolean spawn(IBlockState oreBlock, World world, BlockPos coord, int dimension, boolean cacheOverflow,
			List<IBlockState> blockReplace) {
		if( oreBlock == null ) {
			OreSpawn.LOGGER.fatal("FeatureBase.spawn() called with a null ore!");
			return false;
		}
		
		List<IBlockState> blockToReplace = blockReplace;
		
		if(coord.getY() < 0 || coord.getY() >= world.getHeight()) {
			OreSpawn.LOGGER.warn("Asked to spawn a block outside the permissable Y range - position: %s", coord);
			return false;
		}
		
		if(world.isBlockLoaded(coord)){
			IBlockState targetBlock = world.getBlockState(coord);
			if(canReplace(targetBlock,blockToReplace)) {
				world.setBlockState(coord, oreBlock, 22);
				return true;
			} else {
				return false;
			}
		} else if(cacheOverflow){
			cacheOverflowBlock(oreBlock,coord,dimension);
			return true;
		}
		
		//if it gets here by some strange twist...
		return false;
	}

	protected void cacheOverflowBlock(IBlockState bs, BlockPos coord, int dimension){
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
		Map<BlockPos,IBlockState> cache = overflowCache.getOrDefault(chunkCoord, new HashMap<>());
		cache.put(coord, bs);
	}

	protected Map<BlockPos,IBlockState> retrieveCache(Vec3i chunkCoord ){
		if(overflowCache.containsKey(chunkCoord)){
			Map<BlockPos,IBlockState> cache =overflowCache.get(chunkCoord);
			cacheOrder.remove(chunkCoord);
			overflowCache.remove(chunkCoord);
			return cache;
		} else {
			return Collections.<BlockPos,IBlockState>emptyMap();
		}
	}
	
	protected void scramble(int[] target, Random prng) {
		for(int i = target.length - 1; i > 0; i--){
			int n = prng.nextInt(i);
			int temp = target[i];
			target[i] = target[n];
			target[n] = temp;
		}
	}

	protected boolean canReplace(IBlockState target, List<IBlockState> blockToReplace) {
		if( target.getBlock().equals(Blocks.AIR) ) {
			return false;
		} else {
			return blockToReplace.contains(target);
		}
	}

	protected static final Vec3i[] offsets_small = {
			new Vec3i( 0, 0, 0),new Vec3i( 1, 0, 0),
			new Vec3i( 0, 1, 0),new Vec3i( 1, 1, 0),

			new Vec3i( 0, 0, 1),new Vec3i( 1, 0, 1),
			new Vec3i( 0, 1, 1),new Vec3i( 1, 1, 1)
	};
	
	protected static final Vec3i[] offsets = {
			new Vec3i(-1,-1,-1),new Vec3i( 0,-1,-1),new Vec3i( 1,-1,-1),
			new Vec3i(-1, 0,-1),new Vec3i( 0, 0,-1),new Vec3i( 1, 0,-1),
			new Vec3i(-1, 1,-1),new Vec3i( 0, 1,-1),new Vec3i( 1, 1,-1),

			new Vec3i(-1,-1, 0),new Vec3i( 0,-1, 0),new Vec3i( 1,-1, 0),
			new Vec3i(-1, 0, 0),new Vec3i( 0, 0, 0),new Vec3i( 1, 0, 0),
			new Vec3i(-1, 1, 0),new Vec3i( 0, 1, 0),new Vec3i( 1, 1, 0),

			new Vec3i(-1,-1, 1),new Vec3i( 0,-1, 1),new Vec3i( 1,-1, 1),
			new Vec3i(-1, 0, 1),new Vec3i( 0, 0, 1),new Vec3i( 1, 0, 1),
			new Vec3i(-1, 1, 1),new Vec3i( 0, 1, 1),new Vec3i( 1, 1, 1)
	};
	
	protected static final int[] offsetIndexRef = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26};
	protected static final int[] offsetIndexRef_small = {0,1,2,3,4,5,6,7};
	
	protected static void mergeDefaults(JsonObject parameters, JsonObject defaultParameters ) {
		defaultParameters.entrySet().forEach( entry -> {
			if( !parameters.has(entry.getKey()) ) 
				parameters.add(entry.getKey(), entry.getValue());
		});
	}	
}
