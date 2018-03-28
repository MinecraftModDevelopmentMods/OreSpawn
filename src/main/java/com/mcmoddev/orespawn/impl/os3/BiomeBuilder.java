package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.impl.location.BiomeLocationAcceptAny;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationEmpty;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;
import com.mcmoddev.orespawn.api.os3.IBiomeBuilder;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BiomeBuilder implements IBiomeBuilder {
	private final List<Biome> whitelist = new LinkedList<>();
	private final List<Biome> blacklist = new LinkedList<>();
	
	private boolean acceptAll = false;
	
	public BiomeBuilder() {
		
	}
	
	@Override
	public IBiomeBuilder addWhitelistEntry(Biome biome) {
		this.whitelist.add(biome);
		return this;
	}

	@Override
	public IBiomeBuilder addWhitelistEntry(String biomeName) {
		return this.addWhitelistEntry(new ResourceLocation(biomeName));
	}

	@Override
	public IBiomeBuilder addWhitelistEntry(ResourceLocation biomeResourceLocation) {
		return this.addWhitelistEntry(ForgeRegistries.BIOMES.getValue(biomeResourceLocation));
	}

	@Override
	public IBiomeBuilder addBlacklistEntry(Biome biome) {
		this.blacklist.add(biome);
		return this;
	}

	@Override
	public IBiomeBuilder addBlacklistEntry(String biomeName) {
		return this.addBlacklistEntry(new ResourceLocation(biomeName));
	}

	@Override
	public IBiomeBuilder addBlacklistEntry(ResourceLocation biomeResourceLocation) {
		return this.addBlacklistEntry(ForgeRegistries.BIOMES.getValue(biomeResourceLocation));
	}

	@Override
	public IBiomeBuilder setAcceptAll() {
		this.acceptAll = true;
		return this;
	}

	@Override
	public BiomeLocation create() {
		if (this.acceptAll) {
			return new BiomeLocationAcceptAny();
		}
		
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
