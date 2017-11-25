package com.mcmoddev.orespawn.impl.os3;

import java.util.List;
import java.util.ArrayList;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.impl.location.*;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BiomeBuilderImpl implements BiomeBuilder {
	private List<BiomeLocation> biomeWhitelist;
	private List<BiomeLocation> biomeBlacklist;
	private BiomeLocation loc;

	BiomeBuilderImpl() {
		this.biomeWhitelist = new ArrayList<>();
		this.biomeBlacklist = new ArrayList<>();
	}

	private Biome getBiomeByName(String name) {
		return ForgeRegistries.BIOMES.getValue(new ResourceLocation(name));
	}

	private BiomeDictionary.Type getBiomeDictionaryType(String name) {
		return BiomeDictionary.Type.getType(name);
	}

	@Override
	public BiomeBuilder whitelistBiome(@Nonnull Biome biome) {
		this.biomeWhitelist.add(new BiomeLocationSingle(biome));
		return this;
	}

	@Override
	public BiomeBuilder whitelistBiomeByName(@Nonnull String biomeName) {
		Biome b = getBiomeByName(biomeName);
		BiomeLocation bL = new BiomeLocationSingle(b);

		if (!this.biomeWhitelist.contains(bL)) {
			this.biomeWhitelist.add(bL);
		}

		return this;
	}

	@Override
	public BiomeBuilder whitelistBiomeByDictionary(@Nonnull String biomeDictionaryName) {
		this.biomeWhitelist.add(new BiomeLocationDictionary(getBiomeDictionaryType(biomeDictionaryName)));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiome(@Nonnull Biome biome) {
		this.biomeBlacklist.add(new BiomeLocationSingle(biome));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiomeByName(@Nonnull String biomeName) {
		this.biomeBlacklist.add(new BiomeLocationSingle(getBiomeByName(biomeName)));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiomeByDictionary(@Nonnull String biomeDictionaryName) {
		this.biomeBlacklist.add(new BiomeLocationDictionary(getBiomeDictionaryType(biomeDictionaryName)));
		return this;
	}

	@Override
	public BiomeLocation getBiomes() {
		if (this.loc != null) {
			return this.loc;
		}

		if (!this.biomeBlacklist.isEmpty()) {
			this.loc = new BiomeLocationComposition(ImmutableSet.copyOf(this.biomeWhitelist),
			    ImmutableSet.copyOf(this.biomeBlacklist));
		} else {
			if (this.biomeWhitelist.size() == 1) {
				this.loc = this.biomeWhitelist.toArray(new BiomeLocation[0])[0];
			} else {
				this.loc = new BiomeLocationList(ImmutableSet.copyOf(this.biomeWhitelist));
			}
		}

		return this.loc;
	}

	@Override
	public BiomeBuilder setFromBiomeLocation(@Nonnull BiomeLocation biomes) {
		this.loc = biomes;
		return this;
	}
}
