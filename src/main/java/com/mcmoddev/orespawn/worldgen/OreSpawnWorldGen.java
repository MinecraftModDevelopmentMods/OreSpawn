package com.mcmoddev.orespawn.worldgen;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.data.DefaultOregenParameters;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

public class OreSpawnWorldGen implements IWorldGenerator {

	private final Map<Integer, DimensionLogic> dimensions;
	@SuppressWarnings("unused") private final long nextL;
	
	public OreSpawnWorldGen(Map<Integer, DimensionLogic> allDimensions, long nextLong) {
		this.dimensions = Collections.unmodifiableMap(allDimensions);
		this.nextL = nextLong;
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider) {
		
		int thisDim = world.provider.getDimension();
		DimensionLogic dimensionLogic = this.dimensions.get(thisDim);
		
		if( dimensionLogic == null ) {
			dimensionLogic = this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD);
			if( dimensionLogic == null ) {
//				OreSpawn.LOGGER.fatal("no logic for dimension "+thisDim+" or for all dimensions");
				return;
			}
		}
		
		for( SpawnEntry sE : dimensionLogic.getEntries() ) {
			Biome biome = world.getBiomeProvider().getBiome(new BlockPos(chunkX*16, 64,chunkZ*16));
//			OreSpawn.LOGGER.fatal("Trying to generate in biome "+biome+" for spawn entry with block of type "+sE.getState());
			if( sE.getBiomes().contains(biome) || sE.getBiomes() == Collections.EMPTY_LIST || sE.getBiomes().size() == 0 ) {
				IFeature currentFeatureGen = sE.getFeatureGen();
				// what follows is a stop-gap
				DefaultOregenParameters p = new DefaultOregenParameters( sE.getState(), sE.getMinHeight(), sE.getMaxHeight(), sE.getFrequency(), sE.getVariation(), sE.getSize());
				currentFeatureGen.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider, p);
			}
		}
	}

}
