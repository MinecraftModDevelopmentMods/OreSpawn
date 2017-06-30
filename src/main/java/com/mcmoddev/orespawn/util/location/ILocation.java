package com.mcmoddev.orespawn.util.location;

import net.minecraft.world.biome.Biome;

public interface ILocation {
	boolean matches(Biome biome);
}