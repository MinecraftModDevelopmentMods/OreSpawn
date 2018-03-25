package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface ISpawnBuilder {
	public ISpawnBuilder setName(final String name);
	public ISpawnBuilder setDimensions(final IDimensionList dimensions);
	public ISpawnBuilder setBiomes(final BiomeLocationComposition biomes);
	public ISpawnBuilder setEnabled(final boolean enabled);
	public ISpawnBuilder setRetrogen(final boolean retrogen);
	public ISpawnBuilder setReplacement(final IReplacementEntry replacements);
	public ISpawnBuilder setFeature(final IFeatureEntry feature);
	public ISpawnBuilder addBlock(final String blockName);
	public ISpawnBuilder addBlock(final String blockName, final String blockState);
	public ISpawnBuilder addBlock(final String blockName, final int blockMetadata);
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation);
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation, final String blockState);
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation, final int blockMetadata);
	public ISpawnBuilder addBlock(final Block block);
	public ISpawnBuilder addBlock(final IBlockState block);
	public ISpawnBuilder addBlockWithChance(final String blockName, final int chance);
	public ISpawnBuilder addBlockWithChance(final String blockName, final String blockState, final int chance);
	public ISpawnBuilder addBlockWithChance(final String blockName, final int blockMetadata, final int chance);
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final int chance);
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final String blockState, final int chance);
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation, final int blockMetadata, final int chance);
	public ISpawnBuilder addBlockWithChance(final Block block, final int chance);
	public ISpawnBuilder addBlockWithChance(final IBlockState block, final int chance);
	public ISpawnEntry create();
}
