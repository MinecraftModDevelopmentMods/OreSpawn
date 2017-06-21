package com.mcmoddev.orespawn.impl;


import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
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
    	if( state.getBlock() != null ) {
    		this.logic.add(new SpawnEntryImpl(state, size, variation, frequency, minHeight, maxHeight, biomes));
    	} else {
    		OreSpawn.LOGGER.warn("Trying to register a non-existent block!");
    	}
        return this;
    }

    @Override
    public DimensionLogic addOre(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, Biome... biomes) {
    	if( state.getBlock() != null ) {
    		this.logic.add(new SpawnEntryImpl(state, size, variation, (float)frequency, minHeight, maxHeight, biomes));
    	} else {
    		OreSpawn.LOGGER.warn("Trying to register a non-existent block!");
    	}
        return this;
    }
    
    @Override
    public Collection<SpawnEntry> getEntries() {
        return Collections.unmodifiableList(this.logic);
    }

    @Override
    public SpawnLogic end() {
        return this.parent;
    }
}
