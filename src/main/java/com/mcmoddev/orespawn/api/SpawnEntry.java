package com.mcmoddev.orespawn.api;

import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.state.IBlockState;

public interface SpawnEntry {
	default public boolean isEnabled() {
		return false;
	}
	
	default public boolean isRetrogen() {
		return false;
	}
	
	public String getSpawnName();
	public boolean dimensionAllowed(final int dimension);
	public boolean biomeAllowed(final ResourceLocation biomeName);
	public boolean biomeAllowed(final Biome biome);
	public FeatureEntry getFeature();
	public BlockMatcher getMatcher();
	public ImmutableList<IBlockState> getOreList();
	public ImmutableList<Pair<IBlockState,Integer>> getOreListWithChances();
	
}
