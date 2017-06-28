package com.mcmoddev.orespawn.api;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

public interface SpawnEntry {
    IBlockState getState();

    List<Biome> getBiomes();
    
    IFeature getFeatureGen();
    
    JsonObject getParameters();

	IBlockState getReplacement();
}
