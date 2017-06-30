package com.mcmoddev.orespawn.util.location;

import com.google.common.collect.ImmutableSet;

import net.minecraft.world.biome.Biome;

public class LocationList implements ILocation {
	private final ImmutableSet<ILocation> locations;

	private final int hash;

	public LocationList(ImmutableSet<ILocation> locations) {
		this.locations = locations;
		hash = locations.hashCode();
	}

	@Override
	public boolean matches(Biome biome) {
		return locations.stream().anyMatch(loc -> loc.matches(biome));
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof LocationList && locations.equals(((LocationList) obj).locations);
}
}
