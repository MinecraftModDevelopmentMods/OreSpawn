package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

public interface IBiomeBuilder {
	public IBiomeBuilder addWhitelistEntry(final Biome biome);
	public IBiomeBuilder addWhitelistEntry(final String biomeName);
	public IBiomeBuilder addWhitelistEntry(final ResourceLocation biomeResourceLocation);
	public IBiomeBuilder addBlacklistEntry(Biome biome);
	public IBiomeBuilder addBlacklistEntry(final String biomeName);
	public IBiomeBuilder addBlacklistEntry(final ResourceLocation biomeResourceLocation);
	public IBiomeBuilder setAcceptAll();
	public BiomeLocationComposition create();
}
