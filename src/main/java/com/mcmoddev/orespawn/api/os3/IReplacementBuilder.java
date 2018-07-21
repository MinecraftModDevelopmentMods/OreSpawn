package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface IReplacementBuilder {

	/**
	 * 
	 * @param entryName
	 * @return
	 */
	public IReplacementBuilder setFromName(final String entryName);

	/**
	 * 
	 * @param name
	 * @return
	 */
	public IReplacementBuilder setName(final String name);

	/**
	 * 
	 * @param blockState
	 * @return
	 */
	public IReplacementBuilder addEntry(final IBlockState blockState);

	/**
	 * 
	 * @param blockName
	 * @return
	 */
	public IReplacementBuilder addEntry(final String blockName);

	/**
	 * 
	 * @param blockName
	 * @param state
	 * @return
	 */
	public IReplacementBuilder addEntry(final String blockName, final String state);

	/**
	 * 
	 * @param blockName
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IReplacementBuilder addEntry(final String blockName, final int metadata);

	/**
	 * 
	 * @param blockResourceLocation
	 * @return
	 */
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation);

	/**
	 *
	 * @param blockResourceLocation
	 * @param state
	 * @return
	 */
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation,
			final String state);

	/**
	 *
	 * @param blockResourceLocation
	 * @param metadata
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation,
			final int metadata);

	public boolean hasEntries();

	public IReplacementEntry create();
}
