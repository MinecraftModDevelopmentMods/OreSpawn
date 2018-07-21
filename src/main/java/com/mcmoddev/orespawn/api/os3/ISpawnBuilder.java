package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IDimensionList;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface ISpawnBuilder {

	ISpawnBuilder setName(String name);

	ISpawnBuilder setDimensions(IDimensionList dimensions);

	ISpawnBuilder setBiomes(BiomeLocation biomes);

	ISpawnBuilder setEnabled(boolean enabled);

	ISpawnBuilder setRetrogen(boolean retrogen);

	ISpawnBuilder setReplacement(IReplacementEntry replacements);

	ISpawnBuilder setFeature(IFeatureEntry feature);

	ISpawnBuilder addBlock(String blockName);

	ISpawnBuilder addBlock(String blockName, String blockState);

	ISpawnBuilder addBlock(String blockName, int blockMetadata);

	ISpawnBuilder addBlock(ResourceLocation blockResourceLocation);

	ISpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			String blockState);

	ISpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			int blockMetadata);

	ISpawnBuilder addBlock(Block block);

	ISpawnBuilder addBlock(IBlockState block);

	ISpawnBuilder addBlockWithChance(String blockName, int chance);

	ISpawnBuilder addBlockWithChance(String blockName, String blockState,
			int chance);

	ISpawnBuilder addBlockWithChance(String blockName, int blockMetadata,
			int chance);

	ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int chance);

	ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			String blockState, int chance);

	ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int blockMetadata, int chance);

	ISpawnBuilder addBlockWithChance(Block block, int chance);

	ISpawnBuilder addBlockWithChance(IBlockState block, int chance);

	ISpawnEntry create();

	ISpawnBuilder addBlock(IBlockDefinition block);
}
