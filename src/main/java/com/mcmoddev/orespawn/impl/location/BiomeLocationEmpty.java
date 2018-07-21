package com.mcmoddev.orespawn.impl.location;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.world.biome.Biome;

public class BiomeLocationEmpty implements BiomeLocation {

	@Override
	public boolean matches(Biome biome) {
		return false;
	}

	@Override
	public JsonElement serialize() {
		return new JsonArray();
	}

}
