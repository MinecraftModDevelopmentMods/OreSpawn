package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.DimensionList;
import com.mcmoddev.orespawn.api.FeatureEntry;
import com.mcmoddev.orespawn.api.ReplacementEntry;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;

import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SpawnEntry implements com.mcmoddev.orespawn.api.SpawnEntry {
	private final String spawnName;
	private final DimensionList dimensions;
	private final ReplacementEntry replacements;
	private final List<Pair<IBlockState,Integer>> blocks;
	private final BiomeLocationComposition biomes;
	private final FeatureEntry feature;
	private final boolean enabled;
	private final boolean retrogen;
	
	public SpawnEntry(final String spawnName, final boolean enabled, final boolean retrogen,
			final DimensionList dimensions, final BiomeLocationComposition biomes,
			final ReplacementEntry replacements, final List<Pair<IBlockState,Integer>> blocks, 
			final FeatureEntry feature) {
		this.spawnName = spawnName;
		this.enabled = enabled;
		this.retrogen = retrogen;
		this.dimensions = dimensions;
		this.biomes = biomes;
		this.replacements = replacements;
		this.blocks = new LinkedList<>();
		this.blocks.addAll(blocks);
		this.feature = feature;
	}
	
	@Override
	public String getSpawnName() {
		return this.spawnName;
	}

	@Override
	public boolean dimensionAllowed(int dimension) {
		return this.dimensions.matches(dimension);
	}

	@Override
	public boolean biomeAllowed(ResourceLocation biomeName) {
		return this.biomeAllowed(ForgeRegistries.BIOMES.getValue(biomeName));
	}

	@Override
	public boolean biomeAllowed(Biome biome) {
		return this.biomes.matches(biome);
	}

	@Override
	public FeatureEntry getFeature() {
		return this.feature;
	}

	@Override
	public BlockMatcher getMatcher() {
		return this.replacements.getMatcher();
	}

	@Override
	public ImmutableList<IBlockState> getOreList() {
		return ImmutableList.copyOf(this.blocks.stream()
				.map(pair -> pair.getLeft())
				.collect(Collectors.toList()));
	}

	@Override
	public ImmutableList<Pair<IBlockState, Integer>> getOreListWithChances() {
		return ImmutableList.copyOf(this.blocks);
	}

}
