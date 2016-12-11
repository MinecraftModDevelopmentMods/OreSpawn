package mmd.orespawn.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Collection;

public interface DimensionLogic {
    DimensionLogic addOre(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, BiomeGenBase... biomes);

    Collection<SpawnEntry> getEntries();

    SpawnLogic end();
}
