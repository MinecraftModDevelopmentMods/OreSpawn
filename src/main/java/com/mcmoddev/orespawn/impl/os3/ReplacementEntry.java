package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.OreSpawnBlockMatcher;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.registries.IForgeRegistryEntry;

public class ReplacementEntry extends IForgeRegistryEntry.Impl<IReplacementEntry> implements IReplacementEntry {
	private final List<IBlockState> matchVal;
	private OreSpawnBlockMatcher matcher = null;
	
	public ReplacementEntry(final String name, final IBlockState...toMatch) {
		super.setRegistryName(name);
		this.matchVal = Arrays.asList(toMatch);
	}

	public ReplacementEntry(final String name, final List<IBlockState> toMatch) {
		super.setRegistryName(name);
		this.matchVal = new ArrayList<>();
		this.matchVal.addAll(toMatch);
	}
	@Override
	public OreSpawnBlockMatcher getMatcher() {
		if (this.matcher == null) {
			this.matcher = new OreSpawnBlockMatcher(this.matchVal);
		}
		return this.matcher;
	}
	
	public List<IBlockState> getEntries() {
		return ImmutableList.copyOf(this.matchVal);
	}
}
