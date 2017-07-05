package com.mcmoddev.orespawn.impl.features;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;


public class DefaultFeatureGenerator implements IFeature {
	private static final int MAX_CACHE_SIZE = 1024;
	/** overflow cache so that ores that spawn at edge of chunk can 
	 * appear in the neighboring chunk without triggering a chunk-load */
	private static final Map<Vec3i,Map<BlockPos,IBlockState>> overflowCache = new HashMap<>(MAX_CACHE_SIZE);
	private static final Deque<Vec3i> cacheOrder = new LinkedList<>();
	private Random random;
	
	public DefaultFeatureGenerator() {
		this.random = new Random();
	}
	
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, JsonObject parameters, IBlockState block, IBlockState replaceBlock ) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos,IBlockState> cache = retrieveCache(chunkCoord);
		for(Entry<BlockPos,IBlockState> ent : cache.entrySet()){
			spawn(cache.get(ent.getKey()),world,ent.getKey(),world.provider.getDimension(),false,replaceBlock);
		}
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;
		
		int minY = parameters.get("minHeight").getAsInt();
		int maxY = parameters.get("maxHeight").getAsInt();
		int vari = parameters.get("variation").getAsInt();
		float freq = parameters.get("frequency").getAsFloat();
		int size = parameters.get("size").getAsInt();
		
		if(freq >= 1){
			for(int i = 0; i < freq; i++){
				int x = blockX + random.nextInt(8);
				int y = random.nextInt(maxY - minY) + minY;
				int z = blockZ + random.nextInt(8);
				
				final int r;
				if(vari > 0){
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}
				spawnOre( new BlockPos(x,y,z), block, size + r, world, random, replaceBlock);
			}
		} else if(random.nextFloat() < freq){
			int x = blockX + random.nextInt(8);
			int y = random.nextInt(maxY - minY) + minY;
			int z = blockZ + random.nextInt(8);
			final int r;
			if(vari > 0){
				r = random.nextInt(2 * vari) - vari;
			} else {
				r = 0;
			}
			spawnOre( new BlockPos(x,y,z), block, size + r, world, random, replaceBlock);
		}
		
	}

	private static final Vec3i[] offsets = {
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

	private static final Vec3i[] offsets_small = {
			new Vec3i( 0, 0, 0),new Vec3i( 1, 0, 0),
			new Vec3i( 0, 1, 0),new Vec3i( 1, 1, 0),

			new Vec3i( 0, 0, 1),new Vec3i( 1, 0, 1),
			new Vec3i( 0, 1, 1),new Vec3i( 1, 1, 1)
	};
	private static final int[] offsetIndexRef = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26};
	private static final int[] offsetIndexRef_small = {0,1,2,3,4,5,6,7};

	public static void spawnOre( BlockPos blockPos, IBlockState oreBlock, int quantity, World world, Random prng, IBlockState replaceBlock) {
		int count = quantity;
		if(quantity <= 8){
			int[] scrambledLUT = new int[offsetIndexRef_small.length];
			System.arraycopy(offsetIndexRef_small, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,world,blockPos.add(offsets_small[scrambledLUT[--count]]),world.provider.getDimension(),true,replaceBlock);
			}
			return;
		}
		if(quantity < 27){
			int[] scrambledLUT = new int[offsetIndexRef.length];
			System.arraycopy(offsetIndexRef, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,world,blockPos.add(offsets[scrambledLUT[--count]]),world.provider.getDimension(),true,replaceBlock);
			}
			return;
		}
		double radius = Math.pow(quantity, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)(radius * radius);
		fill:{
			if(prng.nextBoolean()){ // switch-up the direction of fill to reduce predictability
				// fill from north-east
				for(int dy = (int)(-1 * radius); dy < radius; dy++){
					for(int dz = (int)(-1 * radius); dz < radius; dz++){
						for(int dx = (int)(-1 * radius); dx < radius; dx++){
							if((dx*dx + dy*dy + dz*dz) <= rSqr){
								spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,replaceBlock);
								count--;
							}
							if(count <= 0) {
								break fill;
							}
						}
					}
				}
			} else {
				// fill from south-west
				for(int dy = (int)(-1 * radius); dy < radius; dy++){
					for(int dx = (int)(radius); dx >= (int)(-1 * radius); dx--){
						for(int dz = (int)(radius); dz >= (int)(-1 * radius); dz--){
							if((dx*dx + dy*dy + dz*dz) <= rSqr){
								spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,replaceBlock);
								count--;
							}
							if(count <= 0) {
								break fill;
							}
						}
					}
				}
			}
		}
		return;
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
	
	private static void spawn(IBlockState b, World w, BlockPos coord, int dimension, boolean cacheOverflow, IBlockState replaceBlock){
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
		defParams.addProperty("frequency", 0.5);
		defParams.addProperty("size", 8);
		return defParams;
	}


	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

}
