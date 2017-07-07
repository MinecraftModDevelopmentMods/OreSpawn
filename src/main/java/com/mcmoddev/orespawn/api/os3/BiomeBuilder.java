package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.world.biome.Biome;

public interface BiomeBuilder {
	// should have left this for @pau101 as he's the genius behind the BiomeLocation stuff
	// but I'm on a roll :)
	
	BiomeBuilder whitelistBiome( @Nonnull Biome biome );
	BiomeBuilder whitelistBiomeByName( @Nonnull String biomeName );
	BiomeBuilder whitelistBiomeByDictionary( @Nonnull String biomeDictionaryName );
	BiomeBuilder blacklistBiome( @Nonnull Biome biome );
	BiomeBuilder blacklistBiomeByName( @Nonnull String biomeName );
	BiomeBuilder blacklistBiomeByDictionary( @Nonnull String biomeDictionaryName );
	BiomeBuilder setFromBiomeLocation( @Nonnull BiomeLocation biomes );
	
	BiomeLocation getBiomes();
}
