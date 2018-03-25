package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.os3.IBlockDefition;

import net.minecraft.block.state.IBlockState;

public class BlockDefinition implements IBlockDefition {
	private final IBlockState blockState;
	private final int blockChance;
	
	public BlockDefinition(final IBlockState blockState, final int chance) {
		this.blockState = blockState;
		this.blockChance = chance;
	}
	
	@Override
	public IBlockState getBlock() {
		return this.blockState;
	}

	@Override
	public int getChance() {
		return this.blockChance;
	}

}
