package com.mcmoddev.orespawn.impl.features;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;

import com.google.common.base.Predicate;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.DefaultOregenParameters;
import com.mcmoddev.orespawn.data.Integer3D;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.oredict.OreDictionary;


public class DefaultFeatureGenerator implements IFeature {
	private static final int maxCacheSize = 1024;
	/** overflow cache so that ores that spawn at edge of chunk can 
	 * appear in the neighboring chunk without triggering a chunk-load */
	private static final Map<Integer3D,Map<BlockPos,IBlockState>> overflowCache = new HashMap<>(maxCacheSize);
	private static final Deque<Integer3D> cacheOrder = new LinkedList<>();
	private static final HashSet<Block> spawnBlocks = new HashSet<>();
	
	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, DefaultOregenParameters p) {
		int hash = ((chunkX * 0x43 + chunkZ) << 16) | random.nextInt();
		
		if(spawnBlocks.isEmpty()){
			// initialize
			spawnBlocks.add(Blocks.STONE);
			spawnBlocks.add(Blocks.NETHERRACK);
			spawnBlocks.add(Blocks.END_STONE);
			for(ItemStack o : OreDictionary.getOres("stone")){
				if(o.getItem() instanceof ItemBlock)
				spawnBlocks.add(((ItemBlock)o.getItem()).getBlock());
			}
		}
		
		// First, load cached blocks for neighboring chunk ore spawns
		Integer3D chunkCoord = new Integer3D(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos,IBlockState> cache = retrieveCache(chunkCoord);
		for(BlockPos pos : cache.keySet()){
//			OreSpawn.LOGGER.fatal("Continuing spawn from previous chunk");
			spawn(cache.get(pos),world,pos,world.provider.getDimension(),false);
		}
		// now to ore spawn

		random.setSeed(random.nextLong() ^ hash);
		random.nextInt(); // prng prime
		
		if(p.frequency >= 1){
//			OreSpawn.LOGGER.fatal("Trying to spawn "+p.blockState.getBlock());
			for(int i = 0; i < p.frequency; i++){
				int x = (chunkX << 4) + random.nextInt(16);
				int y = random.nextInt(p.maxHeight - p.minHeight) + p.minHeight;
				int z = (chunkZ << 4) + random.nextInt(16);
				
				final int r;
				if(p.variation > 0){
					r = random.nextInt(2 * p.variation) - p.variation;
				} else {
					r = 0;
				}
				spawnOre( new BlockPos(x,y,z), p.blockState, p.size + r, world, random);
			}
		} else if(random.nextFloat() < p.frequency){
//			OreSpawn.LOGGER.fatal("Trying to spawn "+p.blockState.getBlock());
			int x = (chunkX << 4) + random.nextInt(16);
			int y = random.nextInt(p.maxHeight - p.minHeight) + p.minHeight;
			int z = (chunkZ << 4) + random.nextInt(16);
			final int r;
			if(p.variation > 0){
				r = random.nextInt(2 * p.variation) - p.variation;
			} else {
				r = 0;
			}
			spawnOre( new BlockPos(x,y,z), p.blockState, p.size + r, world, random);
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

	public static void spawnOre( BlockPos blockPos, IBlockState oreBlock, int quantity, World world, Random prng) {
		int count = quantity;
//		OreSpawn.LOGGER.fatal("Spawning block of "+oreBlock+" at "+blockPos+" with quantity "+quantity);
		if(quantity <= 8){
			int[] scrambledLUT = new int[offsetIndexRef_small.length];
			System.arraycopy(offsetIndexRef_small, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,world,blockPos.add(offsets_small[scrambledLUT[--count]]),world.provider.getDimension(),true);
			}
			return;
		}
		if(quantity < 27){
			int[] scrambledLUT = new int[offsetIndexRef.length];
			System.arraycopy(offsetIndexRef, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,world,blockPos.add(offsets[scrambledLUT[--count]]),world.provider.getDimension(),true);
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
								spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true);
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
								spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true);
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

	private static final Predicate<IBlockState> stonep = new Predicate<IBlockState>(){
		@Override
		public boolean apply(IBlockState input) {
			Block b = input.getBlock();
			if(b == Blocks.AIR) return false;
			return spawnBlocks.contains(b);
		}
	};

	private static void spawn(IBlockState b, World w, BlockPos coord, int dimension, boolean cacheOverflow){
//		OreSpawn.LOGGER.fatal("Trying to spawn block at "+coord+" of type "+b.getBlock());
		if(coord.getY() < 0 || coord.getY() >= w.getHeight()) return;
		if(w.isAreaLoaded(coord, 0)){
			if(w.isAirBlock(coord)) return;
			IBlockState bs = w.getBlockState(coord);
			if(bs.getBlock().isReplaceableOreGen(bs, w, coord, stonep) || spawnBlocks.contains(bs.getBlock())){
				w.setBlockState(coord, b, 2);
			}
		} else if(cacheOverflow){
			cacheOverflowBlock(b,coord,dimension);
		}
	}


	protected static void cacheOverflowBlock(IBlockState bs, BlockPos coord, int dimension){
		Integer3D chunkCoord = new Integer3D(coord.getX() >> 4, coord.getY() >> 4, dimension);
		if(overflowCache.containsKey(chunkCoord) == false){
			cacheOrder.addLast(chunkCoord);
			if(cacheOrder.size() > maxCacheSize){
				Integer3D drop = cacheOrder.removeFirst();
				overflowCache.get(drop).clear();
				overflowCache.remove(drop);
			}
			overflowCache.put(chunkCoord, new HashMap<BlockPos,IBlockState>());
		}
		Map<BlockPos,IBlockState> cache = overflowCache.get(chunkCoord);
		cache.put(coord, bs);
	}

	protected static Map<BlockPos,IBlockState> retrieveCache(Integer3D chunkCoord ){
		if(overflowCache.containsKey(chunkCoord)){
			Map<BlockPos,IBlockState> cache =overflowCache.get(chunkCoord);
			cacheOrder.remove(chunkCoord);
			overflowCache.remove(chunkCoord);
			return cache;
		} else {
			return Collections.EMPTY_MAP;
		}
	}

}
