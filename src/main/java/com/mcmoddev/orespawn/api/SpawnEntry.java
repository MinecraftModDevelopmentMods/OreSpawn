package com.mcmoddev.orespawn.api;

import com.google.gson.JsonObject;

import net.minecraft.block.state.IBlockState;

public interface SpawnEntry {
    IBlockState getState();

    BiomeLocation getLocation();
    
    IFeature getFeatureGen();
    
    JsonObject getParameters();

	IBlockState getReplacement();
}
