package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.Biome;

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
	public boolean matches(Biome biome) {
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
	public ImmutableList<Biome> getBiomes() {
		List<Biome> temp = new LinkedList<>();
		locations.stream().forEach(bl -> temp.addAll(bl.getBiomes()));
		return ImmutableList.copyOf(temp);
	}

	public ImmutableSet<BiomeLocation> getLocations() {
		return this.locations;
	}

	@Override
	public JsonElement serialize() {
		JsonArray rv = new JsonArray();
		this.locations.stream()
		.filter(bl -> (!(bl instanceof BiomeLocationEmpty)))
		.forEach(bl -> rv.add(bl.serialize()));

		return rv;
	}
}
