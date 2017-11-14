package com.mcmoddev.orespawn.impl.features;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.GeneratorParameters;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.util.OreList;

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
	public void generate( World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
	                      GeneratorParameters parameters ) {
		ChunkPos pos = parameters.getChunk();
		List<IBlockState> replaceBlock = new LinkedList<>();
		replaceBlock.addAll( parameters.getReplacements() );
		JsonObject params = parameters.getParameters();
		OreList ores = parameters.getOres();
		BiomeLocation biomes = parameters.getBiomes();

		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;
	
		mergeDefaults(params, getDefaultParameters());

		runCache(chunkX, chunkZ, world, replaceBlock);
		
		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;
		
		int minY = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxY = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int vari = params.get(Constants.FormatBits.VARIATION).getAsInt();
		float freq = params.get(Constants.FormatBits.FREQUENCY).getAsFloat();
		int size = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();

		if(freq >= 1){
			for(int i = 0; i < freq; i++){
				int x = blockX + random.nextInt(16);
				int y = random.nextInt(maxY - minY) + minY;
				int z = blockZ + random.nextInt(16);
				
				final int r;
				if(vari > 0){
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}
				spawnOre( new BlockPos(x,y,z), ores, size + r, world, random, replaceBlock, biomes);
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
			
			spawnOre( new BlockPos(x,y,z), ores, size + r, world, random, replaceBlock, biomes);
		}
		
	}

	private void spawnOre ( BlockPos blockPos, OreList possibleOres, int quantity, World world, Random prng, List<IBlockState> replaceBlock, BiomeLocation biomes ) {
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
				spawn(oreBlock,world,blockPos.add(offs[scrambledLUT[--count]]),world.provider.getDimension(),true,replaceBlock, biomes );
			}
			return;
		}
		
		doSpawnFill( prng.nextBoolean(), world, blockPos, count, replaceBlock, possibleOres, biomes );
	}

	private void doSpawnFill ( boolean nextBoolean, World world, BlockPos blockPos, int quantity, List<IBlockState> replaceBlock, OreList possibleOres, BiomeLocation biomes ) {
		double radius = Math.pow(quantity, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		if( nextBoolean ) {
			spawnMunge( world, blockPos, radius, replaceBlock, quantity, possibleOres, biomes, true );
		} else {
			spawnMunge( world, blockPos, radius, replaceBlock, quantity, possibleOres, biomes, false );
		}
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 0);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 256);
		defParams.addProperty(Constants.FormatBits.VARIATION, 16);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 0.5);
		defParams.addProperty(Constants.FormatBits.NODE_SIZE, 8);
		return defParams;
	}


	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

}
