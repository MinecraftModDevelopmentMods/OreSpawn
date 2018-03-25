package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface IBlockBuilder {
	public IBlockBuilder setFromBlockState(final IBlockState blockState);
	public IBlockBuilder setFromBlock(final Block block);
	public IBlockBuilder setFromName(final String blockName);
	public IBlockBuilder setFromName(final String blockName, final String state);
	@Deprecated
	public IBlockBuilder setFromName(final String blockName, final int metadata);
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation);
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation, final String state);
	@Deprecated
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation, final int metadata);
	public IBlockBuilder setFromBlockStateWithChance(final IBlockState blockState, final int chance);
	public IBlockBuilder setFromBlockWithChance(final Block block, final int chance);
	public IBlockBuilder setFromNameWithChance(final String blockName, final int chance);
	public IBlockBuilder setFromNameWithChance(final String blockName, final String state, final int chance);
	@Deprecated
	public IBlockBuilder setFromNameWithChance(final String blockName, final int metadata, final int chance);
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final int chance);
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final String state, final int chance);
	@Deprecated
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final int metadata, final int chance);
	public IBlockBuilder setChance(int chance);
	public IBlockDefition create();
}
