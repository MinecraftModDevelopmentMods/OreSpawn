package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.os3.IBlockDefinition;

import net.minecraft.block.state.IBlockState;

public class BlockDefinition implements IBlockDefinition {

	private final IBlockState blockState;
	private final int blockChance;
	private final boolean isValid;

	public BlockDefinition(final IBlockState blockState, final int chance, final boolean isValid) {
		this.blockState = blockState;
		this.blockChance = chance;
		this.isValid = isValid;
	}

	@Override
	public IBlockState getBlock() {
		return this.blockState;
	}

	@Override
	public int getChance() {
		return this.blockChance;
	}

	@Override
	public boolean isValid() {
		return this.isValid;
	}
}
