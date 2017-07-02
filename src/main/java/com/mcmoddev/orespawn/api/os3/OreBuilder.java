package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public interface OreBuilder {
	OreBuilder setOre( @Nonnull String name );
	OreBuilder setOre( @Nonnull String name, @Nonnull String serializedState );
	OreBuilder setOre( @Nonnull String name, @Nonnull int metaData );
	OreBuilder setOre( @Nonnull Block base );
	OreBuilder setOre( @Nonnull Block base, @Nonnull String serializedState );
	OreBuilder setOre( @Nonnull Item base, @Nonnull int metaData );
	OreBuilder setOre( @Nonnull ItemStack item );
	OreBuilder setChance( @Nonnull int chance );
	
	IBlockState getOre();
	IBlockState getChance();
}
