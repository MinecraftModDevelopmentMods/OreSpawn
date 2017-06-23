package mmd.orespawn.api;

import java.util.List;

import com.google.gson.JsonObject;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

public interface SpawnEntry {
    IBlockState getState();

    int getSize();

    int getVariation();

    float getFrequency();

    int getMinHeight();

    int getMaxHeight();

    List<Biome> getBiomes();
    
    IFeature getFeatureGen();
    
    JsonObject getParameters();
}
