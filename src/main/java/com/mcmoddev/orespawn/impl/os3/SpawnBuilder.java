package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.api.exceptions.BadStateValueException;
import com.mcmoddev.orespawn.api.os3.IBlockDefinition;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;
import com.mcmoddev.orespawn.util.StateUtil;

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
	public ISpawnBuilder setName(final String name) {
		this.spawnName = name;
		return this;
	}

	@Override
	public ISpawnBuilder setDimensions(final IDimensionList dimensions) {
		this.dimensions = dimensions;
		return this;
	}

	@Override
	public ISpawnBuilder setBiomes(final BiomeLocation biomes) {
		this.biomes = biomes;
		return this;
	}

	@Override
	public ISpawnBuilder setEnabled(final boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	@Override
	public ISpawnBuilder setRetrogen(final boolean retrogen) {
		this.retrogen = retrogen;
		return this;
	}

	@Override
	public ISpawnBuilder setReplacement(final IReplacementEntry replacements) {
		this.replacements = replacements;
		return this;
	}

	@Override
	public ISpawnBuilder setFeature(final IFeatureEntry feature) {
		this.feature = feature;
		return this;
	}

	@Override
	public ISpawnBuilder addBlock(final String blockName) {
		return this.addBlock(new ResourceLocation(blockName));
	}

	@Override
	public ISpawnBuilder addBlock(final String blockName, final String blockState) {
		return this.addBlock(new ResourceLocation(blockName), blockState);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public ISpawnBuilder addBlock(final String blockName, final int blockMetadata) {
		return this.addBlock(new ResourceLocation(blockName), blockMetadata);
	}

	@Override
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation,
			final String blockState) {
		return this.addBlockWithChance(blockResourceLocation, blockState, 100);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public ISpawnBuilder addBlock(final ResourceLocation blockResourceLocation,
			final int blockMetadata) {
		return this.addBlockWithChance(blockResourceLocation, 100);
	}

	@Override
	public ISpawnBuilder addBlock(final Block block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public ISpawnBuilder addBlock(final IBlockState block) {
		return this.addBlockWithChance(block, 100);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final String blockName, final int chance) {
		return this.addBlockWithChance(new ResourceLocation(blockName), chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final String blockName, final String blockState,
			final int chance) {
		return this.addBlockWithChance(new ResourceLocation(blockName), blockState, chance);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public ISpawnBuilder addBlockWithChance(final String blockName, final int blockMetadata,
			final int chance) {
		return this.addBlockWithChance(blockName, blockMetadata, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation,
			final int chance) {
		final IBlockState tempVar = ForgeRegistries.BLOCKS.getValue(blockResourceLocation)
				.getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation,
			final String blockState, final int chance) {
		final Block tempBlock = ForgeRegistries.BLOCKS.getValue(blockResourceLocation);
		IBlockState tempVar;
			try {
				tempVar = StateUtil.deserializeState(tempBlock, blockState);
			} catch (BadStateValueException e) {
				StringBuilder p = new StringBuilder();
				for(StackTraceElement elem: e.getStackTrace()) p.append(String.format("%s.%s (%s:%u)\n", elem.getClassName(), elem.getMethodName(), elem.getFileName(), elem.getLineNumber()));
				OreSpawn.LOGGER.error(String.format("Exception: %s\n%s", e.getMessage(), p.toString()));
				tempVar = tempBlock.getDefaultState();
			}
		return this.addBlockWithChance(tempVar, chance);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public ISpawnBuilder addBlockWithChance(final ResourceLocation blockResourceLocation,
			final int blockMetadata, final int chance) {
		final IBlockState tempVar = ForgeRegistries.BLOCKS.getValue(blockResourceLocation)
				.getStateFromMeta(blockMetadata);
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final Block block, final int chance) {
		final IBlockState tempVar = block.getDefaultState();
		return this.addBlockWithChance(tempVar, chance);
	}

	@Override
	public ISpawnBuilder addBlockWithChance(final IBlockState block, final int chance) {
		final BlockBuilder bb = new BlockBuilder();
		bb.setFromBlockStateWithChance(block, chance);
		return this.addBlock(bb.create());
	}

	@Override
	public ISpawnBuilder addBlock(final IBlockDefinition block) {
		if (block.isValid()) {
			this.blocks.addBlock(block);
		}
		return this;
	}

	@Override
	public SpawnEntry create() {
		if (this.blocks.count() > 0) {
			return new SpawnEntry(this.spawnName, this.enabled, this.retrogen, this.dimensions,
					this.biomes, this.replacements, this.blocks, this.feature);
		} else {
			return null;
		}
	}
}
