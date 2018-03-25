package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraftforge.registries.IForgeRegistryEntry;

public interface IReplacementEntry extends IForgeRegistryEntry<IReplacementEntry> {
	public BlockMatcher getMatcher();
	public IBlockState getBlockState();
}
