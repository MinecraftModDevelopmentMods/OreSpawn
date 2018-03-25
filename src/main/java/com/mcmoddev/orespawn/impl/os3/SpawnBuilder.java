package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.mcmoddev.orespawn.api.DimensionList;
import com.mcmoddev.orespawn.api.FeatureEntry;
import com.mcmoddev.orespawn.api.ReplacementEntry;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SpawnBuilder implements com.mcmoddev.orespawn.api.os3.SpawnBuilder {
	private String spawnName;
	private boolean enabled;
	private boolean retrogen;
	private List<Pair<IBlockState,Integer>> blocks = new LinkedList<>();
	private FeatureEntry feature;
	private BiomeLocationComposition biomes;
	private DimensionList dimensions;
	private ReplacementEntry replacements;
	
	public SpawnBuilder() {
		this.enabled = false;
		this.retrogen = false;
	}
	
	public SpawnBuilder(final String spawnName) {
		this();
		this.spawnName = spawnName;
	}
	
	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setName(String name) {
		this.spawnName = name;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setDimensions(DimensionList dimensions) {
		this.dimensions = dimensions;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setBiomes(BiomeLocationComposition biomes) {
		this.biomes = biomes;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setRetrogen(boolean retrogen) {
		this.retrogen = retrogen;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setReplacement(ReplacementEntry replacements) {
		this.replacements = replacements;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder setFeature(FeatureEntry feature) {
		this.feature = feature;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(String blockName) {
		return this.addBlock(new ResourceLocation(blockName));
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(String blockName, String blockState) {
		return this.addBlock(new ResourceLocation(blockName), blockState);
	}

	@Override
	@Deprecated
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(String blockName, int blockMetadata) {
		return this.addBlock(new ResourceLocation(blockName), blockMetadata);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(ResourceLocation blockResourceLocation) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			String blockState) {
		return this.addBlockWithChance(blockResourceLocation, blockState, 100);
	}

	@Override
	@Deprecated
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			int blockMetadata) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(Block block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlock(IBlockState block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(String blockName, int chance) {
		return this.addBlockWithChance( new ResourceLocation(blockName), chance);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(String blockName, String blockState,
			int chance) {
		return this.addBlockWithChance(new ResourceLocation(blockName), blockState, chance);
	}

	@Override
	@Deprecated
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(String blockName, int blockMetadata,
			int chance) {
		return this.addBlockWithChance(blockName, blockMetadata, chance);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int chance) {
		IBlockState tempVar = ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			String blockState, int chance) {
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		IBlockState tempVar = StateUtil.deserializeState(tempBlock, blockState);
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	@Deprecated
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int blockMetadata, int chance) {
		IBlockState tempVar =  ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getStateFromMeta(blockMetadata);
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(Block block, int chance) {
		IBlockState tempVar = block.getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.SpawnBuilder addBlockWithChance(IBlockState block, int chance) {
		this.blocks.add(Pair.of(block, chance));
		return this;
	}

	@Override
	public SpawnEntry create() {
		return new SpawnEntry(this.spawnName, this.enabled, this.retrogen, this.dimensions,
				this.biomes, this.replacements, this.blocks, this.feature);
	}
}
