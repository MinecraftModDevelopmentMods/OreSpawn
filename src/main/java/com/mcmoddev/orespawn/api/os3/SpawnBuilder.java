package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.DimensionList;
import com.mcmoddev.orespawn.api.FeatureEntry;
import com.mcmoddev.orespawn.api.ReplacementEntry;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface SpawnBuilder {
	public SpawnBuilder setName(final String name);
	public SpawnBuilder setDimensions(final DimensionList dimensions);
	public SpawnBuilder setBiomes(final BiomeLocationComposition biomes);
	public SpawnBuilder setEnabled(final boolean enabled);
	public SpawnBuilder setRetrogen(final boolean retrogen);
	public SpawnBuilder setReplacement(final ReplacementEntry replacements);
	public SpawnBuilder setFeature(final FeatureEntry feature);
	public SpawnBuilder addBlock(final String blockName);
	public SpawnBuilder addBlock(final String blockName, final String blockState);
	public SpawnBuilder addBlock(final String blockName, final int blockMetadata);
	public SpawnBuilder addBlock(final ResourceLocation blockResourceLocation);
	public SpawnBuilder addBlock(final ResourceLocation blockResourceLocation, final String blockState);
	public SpawnBuilder addBlock(final ResourceLocation blockResourceLocation, final int blockMetadata);
	public SpawnBuilder addBlock(final Block block);
	public SpawnBuilder addBlock(final IBlockState block);
	public SpawnBuilder addBlockWithChance(final String blockName, final int chance);
	public SpawnBuilder addBlockWithChance(final String blockName, final String blockState, final int chance);
	public SpawnBuilder addBlockWithChance(final String blockName, final int blockMetadata, final int chance);
	public SpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final int chance);
	public SpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final String blockState, final int chance);
	public SpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final int blockMetadata, final int chance);
	public SpawnBuilder addBlockWithChance(final Block block, final int chance);
	public SpawnBuilder addBlockWithChance(final IBlockState block, final int chance);
	public SpawnEntry create();
}
