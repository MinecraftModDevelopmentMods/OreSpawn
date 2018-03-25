package com.mcmoddev.orespawn.api;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

public interface BiomeBuilder {
	public BiomeBuilder addWhitelistEntry(final Biome biome);
	public BiomeBuilder addWhitelistEntry(final String biomeName);
	public BiomeBuilder addWhitelistEntry(final ResourceLocation biomeResourceLocation);
	public BiomeBuilder addBlacklistEntry(Biome biome);
	public BiomeBuilder addBlacklistEntry(final String biomeName);
	public BiomeBuilder addBlacklistEntry(final ResourceLocation biomeResourceLocation);
	public BiomeBuilder setAcceptAll();
	public BiomeLocation create();
}
