package com.mcmoddev.orespawn.impl.os3;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableSet;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.impl.location.*;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BiomeBuilderImpl implements BiomeBuilder {
	private Set<BiomeLocation> biomeWhitelist;
	private Set<BiomeLocation> biomeBlacklist;
	private BiomeLocation loc;
	
	public BiomeBuilderImpl() {
		this.biomeWhitelist = new TreeSet<>();
		this.biomeBlacklist = new TreeSet<>();
	}
	
	private Biome getBiome(String name) {
		return ForgeRegistries.BIOMES.getValue(new ResourceLocation(name));
	}

	private BiomeDictionary.Type getBiomeDictionaryType(String name) {
		return BiomeDictionary.Type.getType(name);
	}
	
	@Override
	public BiomeBuilder whitelistBiome(Biome biome) {
		this.biomeWhitelist.add(new BiomeLocationSingle(biome));
		return this;
	}

	@Override
	public BiomeBuilder whitelistBiomeByName(String biomeName) {
		this.biomeWhitelist.add(new BiomeLocationSingle(getBiome(biomeName)));
		return this;
	}

	@Override
	public BiomeBuilder whitelistBiomeByDictionary(String biomeDictionaryName) {
		this.biomeWhitelist.add(new BiomeLocationDictionary(getBiomeDictionaryType(biomeDictionaryName)));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiome(Biome biome) {
		this.biomeBlacklist.add(new BiomeLocationSingle(biome));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiomeByName(String biomeName) {
		this.biomeBlacklist.add(new BiomeLocationSingle(getBiome(biomeName)));
		return this;
	}

	@Override
	public BiomeBuilder blacklistBiomeByDictionary(String biomeDictionaryName) {
		this.biomeBlacklist.add(new BiomeLocationDictionary(getBiomeDictionaryType(biomeDictionaryName)));
		return this;
	}

	@Override
	public BiomeLocation getBiomes() {
		if( this.loc != null ) {
			return this.loc;
		}
		
		if( !this.biomeBlacklist.isEmpty() ) {
			this.loc = new BiomeLocationComposition(ImmutableSet.<BiomeLocation>copyOf(this.biomeWhitelist),
					ImmutableSet.<BiomeLocation>copyOf(this.biomeBlacklist));
		} else {
			if( this.biomeWhitelist.size() == 1 ) {
				BiomeLocation b = this.biomeWhitelist.toArray(new BiomeLocation[1])[0];
				this.loc = b;
			} else {
				this.loc = new BiomeLocationList(ImmutableSet.<BiomeLocation>copyOf(this.biomeWhitelist));
			}
		}
		return this.loc;
	}

	@Override
	public BiomeBuilder setFromBiomeLocation(BiomeLocation biomes) {
		this.loc = biomes;
		return this;
	}
}
