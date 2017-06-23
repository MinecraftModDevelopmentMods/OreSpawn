package com.mcmoddev.orespawn.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

import java.util.Collection;

import com.google.gson.JsonObject;

public interface DimensionLogic {
    DimensionLogic addOre(IBlockState state, int size, int variation, float frequency, int minHeight, int maxHeight, Biome... biomes);
    
    DimensionLogic addOre(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, Biome... biomes);
    
    Collection<SpawnEntry> getEntries();

    SpawnLogic end();

	DimensionLogic addOre(IBlockState state, int size, int variation, float frequency, int minHeight, int maxHeight,
			Biome[] array, IFeature featureGen, IBlockState blockRep);
	
	DimensionLogic addOre(IBlockState state, JsonObject parameters,	Biome[] array, IFeature featureGen, IBlockState blockRep);
}
