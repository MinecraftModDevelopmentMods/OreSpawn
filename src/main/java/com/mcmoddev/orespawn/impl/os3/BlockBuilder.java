package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.os3.IBlockBuilder;
import com.mcmoddev.orespawn.api.os3.IBlockDefinition;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BlockBuilder implements IBlockBuilder {

	private IBlockState blockState;
	private int chance;
	private boolean isValid = true;

	public BlockBuilder() {
		// nothing to do here
	}

	@Override
	public IBlockBuilder setFromBlockState(final IBlockState blockState) {
		ResourceLocation key = blockState.getBlock().getRegistryName();
		if (!ForgeRegistries.BLOCKS.containsKey(key)) {
			this.isValid = false;
		}
		return this.setFromBlockStateWithChance(blockState, 100);
	}

	@Override
	public IBlockBuilder setFromBlock(final Block block) {
		ResourceLocation key = block.getRegistryName();
		if (!ForgeRegistries.BLOCKS.containsKey(key)) {
			this.isValid = false;
		}
		return this.setFromBlockState(block.getDefaultState());
	}

	@Override
	public IBlockBuilder setFromName(final String blockName) {
		return this.setFromName(new ResourceLocation(blockName));
	}

	@Override
	public IBlockBuilder setFromName(final String blockName, final String state) {
		return this.setFromName(new ResourceLocation(blockName), state);
	}

	@Override
	public IBlockBuilder setFromName(final String blockName, final int metadata) {
		return this.setFromName(new ResourceLocation(blockName), metadata);
	}

	@Override
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		return this.setFromBlock(ForgeRegistries.BLOCKS.getValue(blockResourceLocation));
	}

	@Override
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation, final String state) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		return this.setFromBlockState(StateUtil.deserializeState(tempBlock, state));
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public IBlockBuilder setFromName(final ResourceLocation blockResourceLocation, final int metadata) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		return this.setFromBlockState(tempBlock.getStateFromMeta(metadata));
	}

	@Override
	public IBlockBuilder setFromBlockStateWithChance(final IBlockState blockState, final int chance) {
		ResourceLocation key = blockState.getBlock().getRegistryName();
		if (!ForgeRegistries.BLOCKS.containsKey(key)) {
			this.isValid = false;
		}
		this.blockState = blockState;
		this.chance = chance;
		return this;
	}

	@Override
	public IBlockBuilder setFromBlockWithChance(final Block block, final int chance) {
		ResourceLocation key = block.getRegistryName();
		if (!ForgeRegistries.BLOCKS.containsKey(key)) {
			this.isValid = false;
		}
		return this.setFromBlockStateWithChance(block.getDefaultState(), chance);
	}

	@Override
	public IBlockBuilder setFromNameWithChance(final String blockName, final int chance) {
		return this.setFromNameWithChance(new ResourceLocation(blockName), chance);
	}

	@Override
	public IBlockBuilder setFromNameWithChance(final String blockName, final String state, final int chance) {
		return this.setFromNameWithChance(new ResourceLocation(blockName), state, chance);
	}

	@Override
	public IBlockBuilder setFromNameWithChance(final String blockName, final int metadata, final int chance) {
		return this.setFromNameWithChance(new ResourceLocation(blockName), metadata, chance);
	}

	@Override
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final int chance) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		return this.setFromBlockWithChance(ForgeRegistries.BLOCKS.getValue(blockResourceLocation),
				chance);
	}

	@Override
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final String state,
			final int chance) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		return this.setFromBlockStateWithChance(StateUtil.deserializeState(tempBlock, state),
				chance);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public IBlockBuilder setFromNameWithChance(final ResourceLocation blockResourceLocation, final int metadata,
			final int chance) {
		if (!ForgeRegistries.BLOCKS.containsKey(blockResourceLocation)) {
			this.isValid = false;
		}
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		return this.setFromBlockStateWithChance(tempBlock.getStateFromMeta(metadata), chance);
	}

	@Override
	public IBlockBuilder setChance(final int chance) {
		this.chance = chance;
		return this;
	}

	@Override
	public IBlockDefinition create() {
		return new BlockDefinition(this.blockState, this.chance, this.isValid);
	}
}
