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
	IBlockBuilder setFromBlockState(IBlockState blockState);

	/**
	 *
	 * @param block
	 * @return
	 */
	IBlockBuilder setFromBlock(Block block);

	/**
	 *
	 * @param blockName
	 * @return
	 */
	IBlockBuilder setFromName(String blockName);

	/**
	 *
	 * @param blockName
	 * @param state
	 * @return
	 */
	IBlockBuilder setFromName(String blockName, String state);

	/**
	 *
	 * @param blockName
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	IBlockBuilder setFromName(String blockName, int metadata);

	/**
	 *
	 * @param blockResourceLocation
	 * @return
	 */
	IBlockBuilder setFromName(ResourceLocation blockResourceLocation);

	/**
	 *
	 * @param blockResourceLocation
	 * @param state
	 * @return
	 */
	IBlockBuilder setFromName(ResourceLocation blockResourceLocation,
			String state);

	/**
	 *
	 * @param blockResourceLocation
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	IBlockBuilder setFromName(ResourceLocation blockResourceLocation,
			int metadata);

	/**
	 *
	 * @param blockState
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromBlockStateWithChance(IBlockState blockState,
			int chance);

	/**
	 *
	 * @param block
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromBlockWithChance(Block block, int chance);

	/**
	 *
	 * @param blockName
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromNameWithChance(String blockName, int chance);

	/**
	 *
	 * @param blockName
	 * @param state
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromNameWithChance(String blockName, String state,
			int chance);

	/**
	 *
	 * @param blockName
	 * @param metadata
	 * @param chance
	 * @return
	 * @deprecated
	 */
	@Deprecated
	IBlockBuilder setFromNameWithChance(String blockName, int metadata,
			int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromNameWithChance(ResourceLocation blockResourceLocation,
			int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param state
	 * @param chance
	 * @return
	 */
	IBlockBuilder setFromNameWithChance(ResourceLocation blockResourceLocation,
			String state, int chance);

	/**
	 *
	 * @param blockResourceLocation
	 * @param metadata
	 * @param chance
	 * @return
	 * @deprecated
	 */
	@Deprecated
	IBlockBuilder setFromNameWithChance(ResourceLocation blockResourceLocation,
			int metadata, int chance);

	/**
	 *
	 * @param chance
	 * @return
	 */
	IBlockBuilder setChance(int chance);

	/**
	 *
	 * @return
	 */
	IBlockDefinition create();
}
