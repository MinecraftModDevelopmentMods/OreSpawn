package com.mcmoddev.orespawn.impl.location;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.BiomeLocation;
import net.minecraft.world.biome.BiomeGenBase;

public final class BiomeLocationSingle implements BiomeLocation {
	private final BiomeGenBase biome;

	private final int hash;

	public BiomeLocationSingle(BiomeGenBase biome) {
		this.biome = biome;
		this.hash = biome.hashCode();
	}

	@Override
	public boolean matches(BiomeGenBase biome) {
		return this.biome.equals(biome);
	}

	@Override
	public ImmutableList<BiomeGenBase> getBiomes() {
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

	public BiomeGenBase getBiome() {
		return this.biome;
	}
}
