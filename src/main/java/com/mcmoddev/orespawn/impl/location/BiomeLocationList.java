package com.mcmoddev.orespawn.impl.location;

import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.world.biome.Biome;

public final class BiomeLocationList implements BiomeLocation {

	private final ImmutableSet<BiomeLocation> locations;

	private final int hash;

	public BiomeLocationList(final ImmutableSet<BiomeLocation> locations) {
		this.locations = locations;
		this.hash = locations.hashCode();
	}

	@Override
	public boolean matches(final Biome biome) {
		return this.locations.stream().anyMatch(loc -> loc.matches(biome));
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public boolean equals(final Object obj) {
		return (obj == this) || ((obj instanceof BiomeLocationList)
				&& this.locations.equals(((BiomeLocationList) obj).locations));
	}

	@Override
	public ImmutableList<Biome> getBiomes() {
		final List<Biome> temp = new LinkedList<>();
		locations.stream().forEach(bl -> temp.addAll(bl.getBiomes()));
		return ImmutableList.copyOf(temp);
	}

	public ImmutableSet<BiomeLocation> getLocations() {
		return this.locations;
	}

	@Override
	public JsonElement serialize() {
		final JsonArray rv = new JsonArray();
		this.locations.stream().filter(bl -> (!(bl instanceof BiomeLocationEmpty)))
				.forEach(bl -> rv.add(bl.serialize()));

		return rv;
	}
}
