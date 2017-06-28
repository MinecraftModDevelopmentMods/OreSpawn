package com.mcmoddev.orespawn.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.SpawnEntry;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

public class SpawnEntryImpl implements SpawnEntry {
	private final JsonObject paramStore; 
    private final IBlockState state;
    private final List<Biome> biomes;
    private final IFeature generator;
    private final IBlockState blockRep;

	public SpawnEntryImpl(IBlockState state, JsonObject parameters, Biome[] biomes, IFeature featureGen,
			IBlockState blockRep) {
		this.paramStore = parameters.getAsJsonObject();
    	this.generator = featureGen;
    	this.blockRep = blockRep;
    	this.state = state;
    	
        if( biomes == null || biomes.length == 0 ) {
        	this.biomes = Collections.<Biome>emptyList();
        } else {
        	this.biomes = new ArrayList<>();
        	for( Biome b : biomes ) {
        		this.biomes.add(b);
        	}
        }
	}

	@Override
    public IBlockState getState() {
        return this.state;
    }

    @Override
    public List<Biome> getBiomes() {
        return Collections.unmodifiableList(this.biomes);
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
