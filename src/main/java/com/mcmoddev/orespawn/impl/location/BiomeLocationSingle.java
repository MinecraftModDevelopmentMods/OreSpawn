package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.Biome;

public final class BiomeLocationSingle implements BiomeLocation {
	private final Biome biome;

	private final int hash;

	public BiomeLocationSingle(Biome biome) {
		this.biome = biome;
		this.hash = biome.hashCode();
	}

	@Override
	public boolean matches(Biome biome) {
		return this.biome.equals(biome);
	}

	@Override
	public ImmutableList<Biome> getBiomes() {
		return ImmutableList.of(this.biome);
	}

	@Override
	public int hashCode() {
		return this.hash;
	}

	@Override
	public boolean equals(Object obj) {
		return (obj == this) || ((obj instanceof BiomeLocationSingle) && this.biome.equals(((BiomeLocationSingle) obj).biome));
	}

	public Biome getBiome() {
		return this.biome;
	}

	@Override
	public JsonElement serialize() {
		return new JsonPrimitive(this.biome.getRegistryName().toString());
	}
}
