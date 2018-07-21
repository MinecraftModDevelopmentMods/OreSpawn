package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;

public interface IBlockDefinition {
	public IBlockState getBlock();
	public int getChance();
	default public boolean isValid() {
		return true;
	}
}
