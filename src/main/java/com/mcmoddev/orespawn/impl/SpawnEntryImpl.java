package com.mcmoddev.orespawn.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.impl.features.DefaultFeatureGenerator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

public class SpawnEntryImpl implements SpawnEntry {
    private final IBlockState state;
    private final int size;
    private final int variation;
    private final float frequency;
    private final int minHeight;
    private final int maxHeight;
    private final List<Biome> biomes;
    private final IFeature generator;
    
    public SpawnEntryImpl(IBlockState state, int size, int variation, float frequency, int minHeight, int maxHeight, Biome[] biomes) {
        this.state = state;
        this.size = size;
        this.variation = variation;
        this.frequency = frequency;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        if( biomes == null || biomes.length == 0 ) {
        	this.biomes = Collections.EMPTY_LIST;
        } else {
        	this.biomes = new ArrayList<>();
        	for( Biome b : biomes ) {
        		this.biomes.add(b);
        	}
        }
        this.generator = new DefaultFeatureGenerator();
//        OreSpawn.LOGGER.fatal("OreSpawnAPI: registering block "+state.getBlock()+" for generation");
    }

    @Override
    public IBlockState getState() {
        return this.state;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getVariation() {
        return this.variation;
    }

    @Override
    public float getFrequency() {
        return this.frequency;
    }

    @Override
    public int getMinHeight() {
        return this.minHeight;
    }

    @Override
    public int getMaxHeight() {
        return this.maxHeight;
    }

    @Override
    public List<Biome> getBiomes() {
        return Collections.unmodifiableList(this.biomes);
    }
    
    @Override
    public IFeature getFeatureGen() {
    	return this.generator;
    }
}
