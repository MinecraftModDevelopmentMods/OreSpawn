package com.mcmoddev.orespawn.api.os3;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.state.IBlockState;

public interface ISpawnEntry {
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
	public IFeatureEntry getFeature();
	public OreSpawnBlockMatcher getMatcher();
	public ImmutableList<IBlockState> getOreList();
	public ImmutableList<Pair<IBlockState,Integer>> getOreListWithChances();
	
}
