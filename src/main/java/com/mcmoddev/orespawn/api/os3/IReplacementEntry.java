package com.mcmoddev.orespawn.api.os3;

import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;

public interface IReplacementEntry {
	IReplacementEntry setRegistryName(ResourceLocation name);

	ResourceLocation getRegistryName();

	OreSpawnBlockMatcher getMatcher();

	List<IBlockState> getEntries();
}
