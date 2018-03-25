package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.os3.IReplacementEntry;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ReplacementEntry extends IForgeRegistryEntry.Impl<IReplacementEntry> implements IReplacementEntry {
	private final IBlockState matchVal;
	
	public ReplacementEntry(final String name, final IBlockState toMatch) {
		super.setRegistryName(name);
		this.matchVal = toMatch;
	}

	@Override
	public BlockMatcher getMatcher() {
		return BlockMatcher.forBlock(this.matchVal.getBlock());
	}
	
	@Override
	public IBlockState getBlockState() {
		return this.matchVal;
	}
}
