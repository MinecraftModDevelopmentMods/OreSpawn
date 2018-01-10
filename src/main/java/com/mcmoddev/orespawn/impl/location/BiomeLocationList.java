package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;
import java.util.LinkedList;

public final class BiomeLocationList implements BiomeLocation {
	private final ImmutableSet<BiomeLocation> locations;

	private final int hash;

	public BiomeLocationList(ImmutableSet<BiomeLocation> locations) {
		this.locations = locations;
		this.hash = locations.hashCode();
	}

	@Override
	public boolean matches(BiomeGenBase biome) {
		return this.locations.stream().anyMatch(loc -> loc.matches(biome));
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public boolean equals(Object obj) {
		return (obj == this) || ((obj instanceof BiomeLocationList) && this.locations.equals(((BiomeLocationList) obj).locations));
	}

	@Override
	public ImmutableList<BiomeGenBase> getBiomes() {
		List<BiomeGenBase> temp = new LinkedList<>();
		locations.stream().forEach(bl -> temp.addAll(bl.getBiomes()));
		return ImmutableList.copyOf(temp);
	}

	public ImmutableSet<BiomeLocation> getLocations() {
		return this.locations;
	}
}
