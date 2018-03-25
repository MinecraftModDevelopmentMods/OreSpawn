package com.mcmoddev.orespawn.impl.features;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationEmpty;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BiomeBuilder implements com.mcmoddev.orespawn.api.BiomeBuilder {
	private final List<Biome> whitelist = new LinkedList<>();
	private final List<Biome> blacklist = new LinkedList<>();
	
	private boolean acceptAll = false;
	
	public BiomeBuilder() {
		
	}
	
	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addWhitelistEntry(Biome biome) {
		this.whitelist.add(biome);
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addWhitelistEntry(String biomeName) {
		return this.addWhitelistEntry(new ResourceLocation(biomeName));
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addWhitelistEntry(ResourceLocation biomeResourceLocation) {
		return this.addWhitelistEntry(ForgeRegistries.BIOMES.getValue(biomeResourceLocation));
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addBlacklistEntry(Biome biome) {
		this.blacklist.add(biome);
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addBlacklistEntry(String biomeName) {
		return this.addBlacklistEntry(new ResourceLocation(biomeName));
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder addBlacklistEntry(ResourceLocation biomeResourceLocation) {
		return this.addBlacklistEntry(ForgeRegistries.BIOMES.getValue(biomeResourceLocation));
	}

	@Override
	public com.mcmoddev.orespawn.api.BiomeBuilder setAcceptAll() {
		this.acceptAll = true;
		return this;
	}

	@Override
	public BiomeLocation create() {
		ImmutableSet<BiomeLocation> whitelist;
		ImmutableSet<BiomeLocation> blacklist;
		if (this.whitelist.size() == 0) {
			whitelist = ImmutableSet.of(new BiomeLocationEmpty());
		} else {
			whitelist = ImmutableSet.<BiomeLocation>copyOf(
				this.whitelist.stream()
				.map( biome -> new BiomeLocationSingle(biome) )
				.collect(Collectors.toList()));
		}
		
		if (this.blacklist.size() == 0) {
			blacklist = ImmutableSet.of(new BiomeLocation() {
				public boolean matches(Biome b) {
					return true;
				}
			});
		} else {
			blacklist = ImmutableSet.<BiomeLocation>copyOf(
				this.blacklist.stream()
				.map( biome -> new BiomeLocationSingle(biome) )
				.collect(Collectors.toList()));
		}

		return new BiomeLocationComposition(whitelist, blacklist);
	}

}
