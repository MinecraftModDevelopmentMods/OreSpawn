package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.IReplacementBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.lang3.RandomStringUtils;


public class ReplacementBuilder implements IReplacementBuilder {
	private String replacementName = null;
	private List<IBlockState> entries;
	
	public ReplacementBuilder() {
		this.entries = new LinkedList<>();
	}
	
	@Override
	public IReplacementBuilder setFromName(String entryName) {
		this.replacementName = entryName;
		this.entries.addAll(OreSpawn.API.getReplacement(entryName).getEntries());
		return this;
	}

	@Override
	public IReplacementBuilder setName(String name) {
		this.replacementName = name;
		return this;
	}

	@Override
	public IReplacementBuilder addEntry(IBlockState blockState) {
		this.entries.add(blockState);
		return this;
	}

	@Override
	public IReplacementBuilder addEntry(String blockName) {
		return this.addEntry(new ResourceLocation(blockName));
	}

	@Override
	public IReplacementBuilder addEntry(String blockName, String state) {
		return this.addEntry(new ResourceLocation(blockName), state);
	}

	@Override
	@Deprecated
	public IReplacementBuilder addEntry(String blockName, int metadata) {
		return this.addEntry(new ResourceLocation(blockName), metadata);
	}

	@Override
	public IReplacementBuilder addEntry(ResourceLocation blockResourceLocation) {
		return this.addEntry(ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getDefaultState());
	}

	@Override
	public IReplacementBuilder addEntry(ResourceLocation blockResourceLocation, String state) {
		return this.addEntry(StateUtil.deserializeState(ForgeRegistries.BLOCKS.getValue(blockResourceLocation), state));
	}

	@Override
	@Deprecated
	public IReplacementBuilder addEntry(ResourceLocation blockResourceLocation, int metadata) {
		return this.addEntry(ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getStateFromMeta(metadata));
	}

	@Override
	public IReplacementEntry create() {
		if (this.replacementName == null) {
			this.replacementName = String.format("replacement_%s", RandomStringUtils.randomAlphanumeric(8, 16));
		}
		
		return new ReplacementEntry(this.replacementName, this.entries);
	}

}
