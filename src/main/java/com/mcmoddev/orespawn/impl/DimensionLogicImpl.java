package com.mcmoddev.orespawn.impl;


import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.api.SpawnLogic;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DimensionLogicImpl implements DimensionLogic {
    private final List<SpawnEntry> logic = new ArrayList<>();
    private final SpawnLogic parent;

    public DimensionLogicImpl(SpawnLogic parent) {
        this.parent = parent;
    }

    @Override
    public DimensionLogic addOre(IBlockState state, int size, int variation, float frequency, int minHeight, int maxHeight, Biome... biomes) {
    	return this.addOre(state, size, variation, frequency, minHeight, maxHeight, biomes, "default", null);
    }

    @Override
    public DimensionLogic addOre(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, Biome... biomes) {
    	return this.addOre(state, size, variation, (float)frequency, minHeight, maxHeight, biomes);
    }
    
    @Override
    public Collection<SpawnEntry> getEntries() {
        return Collections.unmodifiableList(this.logic);
    }

    @Override
    public SpawnLogic end() {
        return this.parent;
    }

	@Override
	public DimensionLogic addOre(IBlockState state, int size, int variation, float frequency, int minHeight, int maxHeight,
			Biome[] biomes, IFeature featureGen, IBlockState blockRep) {
		JsonObject p = new JsonObject();
		p.addProperty("size", size);
		p.addProperty("variation", variation);
		p.addProperty("frequency", frequency);
		p.addProperty("minHeight", minHeight);
		p.addProperty("maxHeight", maxHeight);
		return this.addOre(state, p, biomes, featureGen, blockRep);
	}

	@Override
	public DimensionLogic addOre(IBlockState state, JsonObject parameters, Biome[] biomes, IFeature featureGen,
			IBlockState blockRep) {
    	if( state.getBlock() != null ) {
    		this.logic.add(new SpawnEntryImpl(state, parameters, biomes, featureGen, blockRep));
    	} else {
    		OreSpawn.LOGGER.warn("Trying to register a non-existent block!");
    	}
        return this;
	}

	@Override
	public DimensionLogic addOre(IBlockState state, int size, int variation, float frequency, int minHeight,
			int maxHeight, Biome[] biomes, String featureName, IBlockState blockRep) {
		return this.addOre(state, size, variation, frequency, minHeight, maxHeight, biomes, OreSpawn.FEATURES.getFeature(featureName), blockRep);
	}
}
