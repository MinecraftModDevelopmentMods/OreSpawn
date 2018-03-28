package com.mcmoddev.orespawn.impl.location;

import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.world.biome.Biome;

public class BiomeLocationAcceptAny implements BiomeLocation {

	@Override
	public boolean matches(Biome biome) {
		return true;
	}

}
