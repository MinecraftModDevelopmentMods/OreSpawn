package mmd.orespawn.impl;

import com.google.common.collect.ImmutableList;
import mmd.orespawn.api.DimensionLogic;
import mmd.orespawn.api.SpawnEntry;
import mmd.orespawn.api.SpawnLogic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DimensionLogicImpl implements DimensionLogic {
    private final List<SpawnEntry> logic = new ArrayList<>();
    private final SpawnLogic parent;

    public DimensionLogicImpl(SpawnLogic parent) {
        this.parent = parent;
    }

    @Override
    public DimensionLogic addOre(IBlockState state, int size, int variation, int frequency, int minHeight, int maxHeight, Biome... biomes) {
        this.logic.add(new SpawnEntryImpl(state, size, variation, frequency, minHeight, maxHeight, biomes));
        return this;
    }

    @Override
    public Collection<SpawnEntry> getEntries() {
        return ImmutableList.copyOf(this.logic);
    }

    @Override
    public SpawnLogic end() {
        return this.parent;
    }
}
