package com.mcmoddev.orespawn.impl;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.SpawnEntry;

import net.minecraft.block.state.IBlockState;

public class SpawnEntryImpl implements SpawnEntry {
	private final JsonObject paramStore; 
    private final IBlockState state;
    private final BiomeLocation biomes;
    private final IFeature generator;
    private final IBlockState blockRep;

	public SpawnEntryImpl(IBlockState state, JsonObject parameters, BiomeLocation biomes, IFeature featureGen,
			IBlockState blockRep) {
		this.paramStore = parameters.getAsJsonObject();
		this.biomes = biomes;
    	this.generator = featureGen;
    	this.blockRep = blockRep;
    	this.state = state;
	}

	@Override
    public IBlockState getState() {
        return this.state;
    }

    @Override
    public BiomeLocation getLocation() {
        return this.biomes;
    }
    
    @Override
    public IFeature getFeatureGen() {
    	return this.generator;
    }
    
    @Override
    public JsonObject getParameters() {
    	return this.paramStore.getAsJsonObject();
    }
    
    @Override
    public IBlockState getReplacement() {
    	return this.blockRep;
    }
}
