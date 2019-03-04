package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.RandomStringUtils;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.IReplacementBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class ReplacementBuilder implements IReplacementBuilder {

	private String replacementName = null;
	private List<IBlockState> entries;

	public ReplacementBuilder() {
		this.entries = new LinkedList<>();
	}

	@Override
	public IReplacementBuilder setFromName(final String entryName) {
		this.replacementName = entryName;
		this.entries.addAll(OreSpawn.API.getReplacement(entryName).getEntries());
		return this;
	}

	@Override
	public IReplacementBuilder setName(final String name) {
		this.replacementName = name;
		return this;
	}

	@Override
	public IReplacementBuilder addEntry(final IBlockState blockState) {
		this.entries.add(blockState);
		return this;
	}

	@Override
	public IReplacementBuilder addEntry(final String blockName) {
		return this.addEntry(new ResourceLocation(blockName));
	}

	@Override
	public IReplacementBuilder addEntry(final String blockName, final String state) {
		return this.addEntry(new ResourceLocation(blockName), state);
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public IReplacementBuilder addEntry(final String blockName, final int metadata) {
		return this.addEntry(new ResourceLocation(blockName), metadata);
	}

	@Override
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation) {
		return this
				.addEntry(ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getDefaultState());
	}

	@Override
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation,
			final String state) {
		try {
			return this.addEntry(StateUtil
					.deserializeState(ForgeRegistries.BLOCKS.getValue(blockResourceLocation), state));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return this.addEntry(ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getDefaultState());
		}
	}

	/**
	 *
	 * @deprecated
	 */
	@Override
	@Deprecated
	public IReplacementBuilder addEntry(final ResourceLocation blockResourceLocation,
			final int metadata) {
		return this.addEntry(
				ForgeRegistries.BLOCKS.getValue(blockResourceLocation).getStateFromMeta(metadata));
	}

	@Override
	public boolean hasEntries() {
		return !this.entries.isEmpty();
	}

	@Override
	public IReplacementEntry create() {
		if (this.replacementName == null) {
			this.replacementName = String.format(Locale.ENGLISH, "replacement_%s",
					RandomStringUtils.randomAlphanumeric(8, 16));
		}

		return new ReplacementEntry(this.replacementName, this.entries);
	}
}
