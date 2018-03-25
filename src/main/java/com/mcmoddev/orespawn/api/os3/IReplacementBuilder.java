package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface IReplacementBuilder {
	public IReplacementBuilder setFromName(final String entryName);
	public IReplacementBuilder setName(final String name);
	public IReplacementBuilder addEntry(final IBlockState blockState);
	public IReplacementBuilder addEntry(final String blockName);
	public IReplacementBuilder addEntry(final String blockName, final String state);
	@Deprecated
	public IReplacementBuilder addEntry(final String blockName, final int metadata);
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation);
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation, final String state);
	@Deprecated
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation, final int metadata);
	public IReplacementEntry create();
}
