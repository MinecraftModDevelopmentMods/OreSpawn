package com.mcmoddev.orespawn.api.os3;

import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.registries.IForgeRegistryEntry;

public interface IReplacementEntry extends IForgeRegistryEntry<IReplacementEntry> {

	public OreSpawnBlockMatcher getMatcher();

	public List<IBlockState> getEntries();
}
