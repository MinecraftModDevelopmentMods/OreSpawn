package com.mcmoddev.orespawn.util.location;

import net.minecraft.world.biome.Biome;

public final class BiomeLocation implements ILocation {
	private final Biome biome;

	private final int hash;

	public BiomeLocation(Biome biome) {
		this.biome = biome;
		hash = biome.hashCode();
	}

	@Override
	public boolean matches(Biome biome) {
		return this.biome.equals(biome);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof BiomeLocation && biome == ((BiomeLocation) obj).biome;
	}
}