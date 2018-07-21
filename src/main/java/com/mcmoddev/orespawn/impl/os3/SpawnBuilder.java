package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.util.StateUtil;
import com.mcmoddev.orespawn.api.os3.IBlockDefinition;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SpawnBuilder implements ISpawnBuilder {
	private String spawnName;
	private boolean enabled;
	private boolean retrogen;
	private IBlockList blocks;
	private IFeatureEntry feature;
	private BiomeLocation biomes;
	private IDimensionList dimensions;
	private IReplacementEntry replacements;
	
	public SpawnBuilder() {
		this.enabled = false;
		this.retrogen = false;
		this.blocks = new BlockList();
	}
	
	public SpawnBuilder(final String spawnName) {
		this();
		this.spawnName = spawnName;
	}
	
	@Override
	public ISpawnBuilder setName(String name) {
		this.spawnName = name;
		return this;
	}

	@Override
	public ISpawnBuilder setDimensions(IDimensionList dimensions) {
		this.dimensions = dimensions;
		return this;
	}

	@Override
	public ISpawnBuilder setBiomes(BiomeLocation biomes) {
		this.biomes = biomes;
		return this;
	}

	@Override
	public ISpawnBuilder setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	@Override
	public ISpawnBuilder setRetrogen(boolean retrogen) {
		this.retrogen = retrogen;
		return this;
	}

	@Override
	public ISpawnBuilder setReplacement(IReplacementEntry replacements) {
		this.replacements = replacements;
		return this;
	}

	@Override
	public ISpawnBuilder setFeature(IFeatureEntry feature) {
		this.feature = feature;
		return this;
	}

	@Override
	public ISpawnBuilder addBlock(String blockName) {
		return this.addBlock(new ResourceLocation(blockName));
	}

	@Override
	public ISpawnBuilder addBlock(String blockName, String blockState) {
		return this.addBlock(new ResourceLocation(blockName), blockState);
	}

	@Override
	@Deprecated
	public ISpawnBuilder addBlock(String blockName, int blockMetadata) {
		return this.addBlock(new ResourceLocation(blockName), blockMetadata);
	}

	@Override
	public ISpawnBuilder addBlock(ResourceLocation blockResourceLocation) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public ISpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			String blockState) {
		return this.addBlockWithChance(blockResourceLocation, blockState, 100);
	}

	@Override
	@Deprecated
	public ISpawnBuilder addBlock(ResourceLocation blockResourceLocation,
			int blockMetadata) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public ISpawnBuilder addBlock(Block block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public ISpawnBuilder addBlock(IBlockState block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(String blockName, int chance) {
		return this.addBlockWithChance( new ResourceLocation(blockName), chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(String blockName, String blockState,
			int chance) {
		return this.addBlockWithChance(new ResourceLocation(blockName), blockState, chance);
	}

	@Override
	@Deprecated
	public ISpawnBuilder addBlockWithChance(String blockName, int blockMetadata,
			int chance) {
		return this.addBlockWithChance(blockName, blockMetadata, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int chance) {
		IBlockState tempVar = ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			String blockState, int chance) {
		Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		IBlockState tempVar = StateUtil.deserializeState(tempBlock, blockState);
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	@Deprecated
	public ISpawnBuilder addBlockWithChance(ResourceLocation blockResourceLocation,
			int blockMetadata, int chance) {
		IBlockState tempVar =  ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getStateFromMeta(blockMetadata);
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(Block block, int chance) {
		IBlockState tempVar = block.getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(IBlockState block, int chance) {
		BlockBuilder bb = new BlockBuilder();
		bb.setFromBlockStateWithChance(block, chance);
		return this.addBlock(bb.create());
	}

	@Override 
	public ISpawnBuilder addBlock(IBlockDefinition block) {
		if(block.isValid()) {
			this.blocks.addBlock(block);
		}
		return this;
	}
	
	@Override
	public SpawnEntry create() {
		if(this.blocks.count() > 0) {
			return new SpawnEntry(this.spawnName, this.enabled, this.retrogen, this.dimensions,
					this.biomes, this.replacements, this.blocks, this.feature);
		} else {
			return null;
		}
	}
}
