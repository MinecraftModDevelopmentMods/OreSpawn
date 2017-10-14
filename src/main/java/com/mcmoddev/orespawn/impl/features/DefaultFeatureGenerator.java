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
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;


public class DefaultFeatureGenerator extends FeatureBase implements IFeature {
	public DefaultFeatureGenerator() {
		super( new Random() );
	}
	
	
	@Override
	public void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, JsonObject parameters, BinaryTree ores, IBlockState replaceBlock ) {
		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
		
		mergeDefaults(parameters, getDefaultParameters());

		runCache(chunkX, chunkZ, world, replaceBlock);
		
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
				spawnOre( new BlockPos(x,y,z), ores, size + r, world, random, replaceBlock);
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
			spawnOre( new BlockPos(x,y,z), ores, size + r, world, random, replaceBlock);
		}
		
	}

	public void spawnOre( BlockPos blockPos, BinaryTree possibleOres, int quantity, World world, Random prng, IBlockState replaceBlock) {
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
				IBlockState oreBlock = possibleOres.getRandomOre(prng).getOre();
				spawn(oreBlock,world,blockPos.add(offs[scrambledLUT[--count]]),world.provider.getDimension(),true,replaceBlock);
			}
			return;
		}
		
		doSpawnFill( prng.nextBoolean(), world, blockPos, count, replaceBlock, possibleOres, prng );
		
		return;
	}

	private void doSpawnFill(boolean nextBoolean, World world, BlockPos blockPos, int quantity, IBlockState replaceBlock, BinaryTree possibleOres, Random prng) {
		int count = quantity;
		double radius = Math.pow(quantity, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)(radius * radius);
		if( nextBoolean ) {
			spawnMungeNE( world, blockPos, rSqr, radius, replaceBlock, count, possibleOres, prng );
		} else {
			spawnMungeSW( world, blockPos, rSqr, radius, replaceBlock, count, possibleOres, prng );
		}
	}


	private void spawnMungeSW(World world, BlockPos blockPos, int rSqr, double radius,
			IBlockState replaceBlock, int count, BinaryTree possibleOres, Random prng) {
		int quantity = count;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dx = (int)(radius); dx >= (int)(-1 * radius); dx--){
				for(int dz = (int)(radius); dz >= (int)(-1 * radius); dz--){
					if((dx*dx + dy*dy + dz*dz) <= rSqr){
						IBlockState oreBlock = possibleOres.getRandomOre(prng).getOre();
						spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,replaceBlock);
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
			IBlockState replaceBlock, int count, BinaryTree possibleOres, Random prng) {
		int quantity = count;
		for(int dy = (int)(-1 * radius); dy < radius; dy++){
			for(int dz = (int)(-1 * radius); dz < radius; dz++){
				for(int dx = (int)(-1 * radius); dx < radius; dx++){
					if((dx*dx + dy*dy + dz*dz) <= rSqr){
						IBlockState oreBlock = possibleOres.getRandomOre(prng).getOre();
						spawn(oreBlock,world,blockPos.add(dx,dy,dz),world.provider.getDimension(),true,replaceBlock);
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
