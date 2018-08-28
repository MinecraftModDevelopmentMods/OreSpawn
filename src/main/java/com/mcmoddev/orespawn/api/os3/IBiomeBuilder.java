package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

public interface IBiomeBuilder {

    IBiomeBuilder addWhitelistEntry(Biome biome);

    IBiomeBuilder addWhitelistEntry(String biomeName);

    IBiomeBuilder addWhitelistEntry(ResourceLocation biomeResourceLocation);

    IBiomeBuilder addBlacklistEntry(Biome biome);

    IBiomeBuilder addBlacklistEntry(String biomeName);

    IBiomeBuilder addBlacklistEntry(ResourceLocation biomeResourceLocation);

    IBiomeBuilder setAcceptAll();

    BiomeLocation create();
}
