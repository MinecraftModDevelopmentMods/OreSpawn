package com.mcmoddev.orespawn.api;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Binary-compatible union of the OS3 3.2 and 3.3 biome contracts. */
public interface BiomeLocation {
	boolean matches(Biome biome);

	default JsonElement serialize() {
		return new JsonArray();
	}

	default ImmutableList<Biome> getBiomes() {
		ImmutableList.Builder<Biome> result = ImmutableList.builder();
		for (Biome biome : ForgeRegistries.BIOMES.getValues()) {
			if (matches(biome)) result.add(biome);
		}
		return result.build();
	}
}
