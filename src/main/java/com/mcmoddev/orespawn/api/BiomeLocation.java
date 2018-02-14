package com.mcmoddev.orespawn.api;

import com.google.common.collect.ImmutableList;

import com.mcmoddev.orespawn.util.Collectors2;
import net.minecraft.world.biome.BiomeGenBase;

public interface BiomeLocation {
	boolean matches(BiomeGenBase biome);

default ImmutableList<BiomeGenBase> getBiomes() {
		return ForgeRegistries.BIOMES.getValues().stream().filter(this::matches).collect(Collectors2.toImmutableList());
	}
}
