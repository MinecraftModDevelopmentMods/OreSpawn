package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public interface OreBuilder {
	OreBuilder setOre( @Nonnull String name );
	OreBuilder setOre( @Nonnull String name, @Nonnull String serializedState );
	OreBuilder setOre( @Nonnull String name, int metaData );
	OreBuilder setOre( @Nonnull Block base );
	OreBuilder setOre( @Nonnull Block base, @Nonnull String serializedState );
	OreBuilder setOre( @Nonnull Item base, int metaData );
	OreBuilder setOre( @Nonnull ItemStack item );
	// Allow specifying size as an extra parameter - for those cases where it could be meta
	// or size, skip them to save a potential error condition
	OreBuilder setOre( @Nonnull String name, @Nonnull String serializedState, int chance );
	OreBuilder setOre( @Nonnull String name, int metaData, int chance );
	OreBuilder setOre( @Nonnull Block base, @Nonnull String serializedState, int chance );
	OreBuilder setOre( @Nonnull Item base, int metaData, int chance );
	OreBuilder setOre( @Nonnull ItemStack item, int chance );
	OreBuilder setChance( @Nonnull int chance );
	
	IBlockState getOre();
	int getChance();
}
