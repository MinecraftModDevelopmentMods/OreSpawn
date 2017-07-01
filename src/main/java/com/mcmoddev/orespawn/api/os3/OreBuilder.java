package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public interface OreBuilder {
	IBlockState getOre( String name );
	IBlockState getOre( String name, String serializedState );
	IBlockState getOre( String name, int metaData );
	IBlockState getOre( Block base );
	IBlockState getOre( Block base, String serializedState );
	IBlockState getOre( Item base, int metaData );
	IBlockState getOre( ItemStack item );
}
