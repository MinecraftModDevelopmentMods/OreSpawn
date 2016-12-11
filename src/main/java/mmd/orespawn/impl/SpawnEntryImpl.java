package mmd.orespawn.impl;

import mmd.orespawn.api.SpawnEntry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.BiomeGenBase;

public class SpawnEntryImpl implements SpawnEntry {
    private final IBlockState state;
    private final int size;
    private final int variation;
    private final int frequency;
    private final int minHeight;
    private final int maxHeight;
    private final BiomeGenBase[] biomes;

    public SpawnEntryImpl(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, BiomeGenBase[] biomes) {
        this.state = state;
        this.size = size;
        this.variation = variation;
        this.frequency = frequency;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.biomes = biomes;
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
    public int getFrequency() {
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
    public BiomeGenBase[] getBiomes() {
        return this.biomes;
    }
}
