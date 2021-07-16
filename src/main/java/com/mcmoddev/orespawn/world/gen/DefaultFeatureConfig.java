package com.mcmoddev.orespawn.world.gen;

import com.electronwill.nightconfig.core.conversion.InvalidValueException;
import com.mcmoddev.orespawn.data.BiomeMatcher;
import com.mcmoddev.orespawn.data.DimensionMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.pattern.BlockMatcher;
import net.minecraft.util.WeightedList;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.IFeatureConfig;

import java.util.stream.Stream;

public class DefaultFeatureConfig implements IFeatureConfig {
	public final BlockMatcher matcher;
	public final int size;
	public final WeightedList<BlockState> blocks;
	public final int minHeight;
	public final int maxHeight;
	public final BiomeMatcher biomeMatch;
	public final DimensionMatcher dimensionMatch;

	public DefaultFeatureConfig(BlockMatcher repl, WeightedList<BlockState> blocks, int sz, int minY, int maxY, BlockMatcher blockMatcher, BiomeMatcher biomeMatcher, DimensionMatcher dimensionMatcher) {
		this.matcher = repl;
		this.size = sz;
		this.blocks = blocks;
		this.minHeight = minY;
		this.maxHeight = maxY;
		this.biomeMatch = biomeMatcher;
		this.dimensionMatch = dimensionMatcher;
	}
}
