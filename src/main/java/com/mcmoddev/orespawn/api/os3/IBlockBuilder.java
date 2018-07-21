package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface IBlockBuilder {

	/**
	 *
	 * @param blockState
	 * @return
	 */
	public IBlockBuilder setFromBlockState(final IBlockState blockState);

	/**
	 *
	 * @param block
	 * @return
	 */
	public IBlockBuilder setFromBlock(final Block block);

	/**
	 *
	 * @param blockName
	 * @return
	 */
	public IBlockBuilder setFromName(final String blockName);

	/**
	 *
	 * @param blockName
	 * @param state
	 * @return
	 */
	public IBlockBuilder setFromName(final String blockName, final String state);

	/**
	 *
	 * @param blockName
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IBlockBuilder setFromName(final String blockName, final int metadata);

	/**
	 *
	 * @param blockResourceLocation
	 * @return
	 */
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation);

	/**
	 *
	 * @param blockResourceLocation
	 * @param state
	 * @return
	 */
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation,
			final String state);

	/**
	 *
	 * @param blockResourceLocation
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation,
			final int metadata);

	/**
	 *
	 * @param blockState
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromBlockStateWithChance(final IBlockState blockState,
			final int chance);

	/**
	 *
	 * @param block
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromBlockWithChance(final Block block, final int chance);

	/**
	 *
	 * @param blockName
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromNameWithChance(final String blockName, final int chance);

	/**
	 *
	 * @param blockName
	 * @param state
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromNameWithChance(final String blockName, final String state,
			final int chance);

	/**
	 *
	 * @param blockName
	 * @param metadata
	 * @param chance
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IBlockBuilder setFromNameWithChance(final String blockName, final int metadata,
			final int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation,
			final int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param state
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation,
			final String state, final int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param metadata
	 * @param chance
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation,
			final int metadata, final int chance);

	/**
	 *
	 * @param chance
	 * @return
	 */
	public IBlockBuilder setChance(int chance);

	/**
	 *
	 * @return
	 */
	public IBlockDefinition create();
}
