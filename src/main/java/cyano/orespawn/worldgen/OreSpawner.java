package cyano.orespawn.worldgen;

import com.google.common.base.Predicate;
import cyano.orespawn.OreSpawn;
import cyano.orespawn.events.OreGenEvent;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;
import java.util.stream.Collectors;

public class OreSpawner implements IWorldGenerator {

	private static final int maxCacheSize = 1024;
	/** overflow cache so that ores that spawn at edge of chunk can 
	 * appear in the neighboring chunk without triggering a chunk-load */
	private static final Map<Integer3D,Map<BlockPos,IBlockState>> overflowCache = new HashMap<>(maxCacheSize);
	private static final Deque<Integer3D> cacheOrder = new LinkedList<>();
	private static final HashSet<Block> spawnBlocks = new HashSet<>();

	private static final Set<Integer> registeredDimensions = new HashSet<>();

	private final long hash; // used to make prng's different
	private final Integer dimension; // can be null

	private final OreSpawnData spawnData;

	public OreSpawner(Block oreBlock, int minHeight, int maxHeight, float spawnFrequency, int spawnQuantity, int spawnQuantityVariation, int dimension, long hash){
		this(oreBlock,0,minHeight,maxHeight,spawnFrequency,spawnQuantity,spawnQuantityVariation,null,dimension,hash);
	}
	public OreSpawner(Block oreBlock, int metaDataValue, int minHeight, int maxHeight, float spawnFrequency, int spawnQuantity, int spawnQuantityVariation, int dimension, long hash){
		this(oreBlock,metaDataValue,minHeight,maxHeight,spawnFrequency,spawnQuantity,spawnQuantityVariation,null, dimension,hash);
	}
	public OreSpawner(Block oreBlock, int metaDataValue, int minHeight, int maxHeight, float spawnFrequency, int spawnQuantity, int spawnQuantityVariation, Collection<String> biomes, int dimension, long hash){
		//	oreGen = new WorldGenMinable(oreBlock, 0, spawnQuantity, Blocks.stone);
		this(new OreSpawnData(oreBlock, metaDataValue, minHeight, maxHeight, spawnFrequency, spawnQuantity, spawnQuantityVariation, biomes),dimension,hash);
	}
	public OreSpawner(OreSpawnData spawnData, Integer dimension, long hash){
		this.spawnData = spawnData;
		this.hash = hash;
		this.dimension = dimension;
		if(dimension != null)registeredDimensions.add(dimension);
	}


	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
                         IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		if(spawnBlocks.isEmpty()){
			// initialize
			spawnBlocks.add(Blocks.STONE);
			spawnBlocks.add(Blocks.NETHERRACK);
			spawnBlocks.add(Blocks.END_STONE);
			for(ItemStack o : OreDictionary.getOres("stone")){
				if(o.getItem() instanceof ItemBlock)
				spawnBlocks.add(((ItemBlock)o.getItem()).getBlock());
			}
			spawnBlocks.addAll(
					OreSpawn.additionalStoneBlocks.stream()
							.map((String id)->Block.getBlockFromName(id))
							.filter((Block b)->b != null)
							.collect(Collectors.toSet())
			);
		}
		// restriction checks
		if(dimension == null){
			// check if misc dimension (do not generate if there is any data specific for this dimension) 
			if(registeredDimensions.contains(Integer.valueOf(world.provider.getDimension()))) {
				// do not generate misc dimension ores in non-misc dimension
				return;
			}
		} else if(world.provider.getDimension() != this.dimension.intValue()){
			// wrong dimension
			return;
		}
		BlockPos coord = new BlockPos((chunkX << 4) & 0x08,64,(chunkZ << 4) & 0x08);
		
		
		if(spawnData.restrictBiomes){
			Biome biome = world.getBiome(coord);
			if(!(
                    spawnData.biomesByName.contains(biome.getBiomeName())
					|| spawnData.biomesByName.contains(String.valueOf(Biome.getIdForBiome(biome)))
                )
             ){
				// wrong biome
				return;
			}
		}
		// First, load cached blocks for neighboring chunk ore spawns
		Integer3D chunkCoord = new Integer3D(chunkX, chunkZ, world.provider.getDimension());
		Map<BlockPos,IBlockState> cache = retrieveCache(chunkCoord);
		for(BlockPos p : cache.keySet()){
			//	FMLLog.info("Placed block "+cache.get(p)+" from cache at "+p);
			spawn(cache.get(p),world,p,world.provider.getDimension(),false);
		}
		// now to ore spawn

		random.setSeed(random.nextLong() ^ hash);
		random.nextInt(); // prng prime
		if(spawnData.frequency >= 1){
			for(int i = 0; i < spawnData.frequency; i++){
				int x = (chunkX << 4) + random.nextInt(16);
				int y = random.nextInt(spawnData.maxY - spawnData.minY) + spawnData.minY;
				int z = (chunkZ << 4) + random.nextInt(16);
				//        FMLLog.info("Generating deposite of "+spawnData.ore.getUnlocalizedName()+" at ("+x+","+y+","+z+")");
				final int r;
				if(spawnData.variation > 0){
					r = random.nextInt(2 * spawnData.variation) - spawnData.variation;
				} else {
					r = 0;
				}
				spawnOre( new BlockPos(x,y,z), spawnData.ore,spawnData.metaData, spawnData.spawnQuantity + r, world, random);
			}
		} else if(random.nextFloat() < spawnData.frequency){
			int x = (chunkX << 4) + random.nextInt(16);
			int y = random.nextInt(spawnData.maxY - spawnData.minY) + spawnData.minY;
			int z = (chunkZ << 4) + random.nextInt(16);
			//    FMLLog.info("Generating deposit of "+spawnData.ore.getUnlocalizedName()+" at ("+x+","+y+","+z+")");
			final int r;
			if(spawnData.variation > 0){
				r = random.nextInt(2 * spawnData.variation) - spawnData.variation;
			} else {
				r = 0;
			}
			spawnOre( new BlockPos(x,y,z), spawnData.ore,spawnData.metaData, spawnData.spawnQuantity + r, world, random);
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

	public static void spawnOre( BlockPos blockPos, Block oreBlock, int metaData, int quantity, World world, Random prng) {
		if(!OreSpawn.forceOreGen){
			// cooperating with the event bus
			OreGenEvent oreEvent = new OreGenEvent(world, prng, blockPos, OreSpawn.MODID);
			net.minecraftforge.common.MinecraftForge.ORE_GEN_BUS.post(oreEvent);
			if(oreEvent.getResult() == Result.DENY) {
				// canceled by other mod
				return;
			}
		}
		int count = quantity;
		if(quantity <= 8){
			int[] scrambledLUT = new int[offsetIndexRef_small.length];
			System.arraycopy(offsetIndexRef_small, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,metaData,world,blockPos.add(offsets_small[scrambledLUT[--count]]),world.provider.getDimension(),true);
			}
			return;
		}
		if(quantity < 27){
			int[] scrambledLUT = new int[offsetIndexRef.length];
			System.arraycopy(offsetIndexRef, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT,prng);
			while(count > 0){
				spawn(oreBlock,metaData,world,blockPos.add(offsets[scrambledLUT[--count]]),world.provider.getDimension(),true);
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
								spawn(oreBlock,metaData,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true);
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
								spawn(oreBlock,metaData,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true);
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

	private static final Predicate stonep = new Predicate<IBlockState>(){
		@Override
		public boolean apply(IBlockState input) {
			Block b = input.getBlock();
			if(b == Blocks.AIR) return false;
			return spawnBlocks.contains(b);
		}
	};

	private static void spawn(Block b, int m, World w, BlockPos coord, int dimension, boolean cacheOverflow){
		if(m == 0){
			spawn( b.getDefaultState(), w,coord,dimension,cacheOverflow);
		} else {
			spawn( b.getStateFromMeta(m), w,coord,dimension,cacheOverflow);
		}
	}

	private static void spawn(IBlockState b, World w, BlockPos coord, int dimension, boolean cacheOverflow){
		if(coord.getY() < 0 || coord.getY() >= w.getHeight()) return;
		if(w.isAreaLoaded(coord, 0)){
			if(w.isAirBlock(coord)) return;
			IBlockState bs = w.getBlockState(coord);
			//	FMLLog.info("Spawning ore block "+b.getUnlocalizedName()+" at "+coord);
			if(bs.getBlock().isReplaceableOreGen(bs, w, coord, stonep) || spawnBlocks.contains(bs.getBlock())){
				w.setBlockState(coord, b, 2);
			}
		} else if(cacheOverflow){
			// cache the block
			//	FMLLog.info("Cached ore block "+block+" at "+coord);
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

	protected static class Integer3D{
		/**
		 * X-coordinate of X,Y,Z coordinate 
		 */
		public final int X;
		/**
		 * Y-coordinate of X,Y,Z coordinate 
		 */
		public final int Y;
		/**
		 * Z-coordinate of X,Y,Z coordinate 
		 */
		public final int Z;
		/**
		 * Creates an integer pair to be used as 2D coordinates
		 * @param x X-coordinate of X,Y,Z coordinate 
		 * @param y Y-coordinate of X,Y,Z coordinate 
		 * @param z Z-coordinate of X,Y,Z coordinate 
		 */
		public Integer3D(int x, int y, int z){
			this.X = x;
			this.Y = y;
			this.Z = z;
		}
		@Override
		public int hashCode(){
			return ((X<< 8) ^ ((Y) ) ^ ((Z) << 16) * 17); 
		}
		@Override
		public boolean equals(Object o){
			if(this == o) return true;
			if(o instanceof Integer3D){
				Integer3D other = (Integer3D)o;
				return other.X == this.X && other.Y == this.Y && other.Z == this.Z;
			}
			return false;
		}

	}

}
