package mmd.orespawn.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

public interface SpawnEntry {
    IBlockState getState();

    int getSize();

    int getVariation();

    int getFrequency();

    int getMinHeight();

    int getMaxHeight();

    Biome[] getBiomes();
}
