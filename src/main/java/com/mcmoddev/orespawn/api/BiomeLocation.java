package com.mcmoddev.orespawn.api;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.mcmoddev.orespawn.util.Collectors2;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public interface BiomeLocation {
	boolean matches(Biome biome);
	
	public JsonElement serialize();
	
	default ImmutableList<Biome> getBiomes() {
		return ForgeRegistries.BIOMES.getValuesCollection().stream().filter(this::matches).collect(Collectors2.toImmutableList());
	}
}
