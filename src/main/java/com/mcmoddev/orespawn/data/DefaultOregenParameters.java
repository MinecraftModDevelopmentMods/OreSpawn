package com.mcmoddev.orespawn.data;

import net.minecraft.block.state.IBlockState;

public class DefaultOregenParameters {
	public final IBlockState blockState;
	public final int minHeight;
	public final int maxHeight;
	public final float frequency;
	public final int variation;
	public final int size;
	
	public DefaultOregenParameters(IBlockState blockState, int minHeight, int maxHeight, float frequency, int variation, int size) {
		this.blockState = blockState;
		this.minHeight = minHeight;
		this.maxHeight = maxHeight;
		this.frequency = frequency;
		this.variation = variation;
		this.size = size;
	}
}
