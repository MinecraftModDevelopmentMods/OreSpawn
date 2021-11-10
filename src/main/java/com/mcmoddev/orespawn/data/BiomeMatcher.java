package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.utils.codecs.AllowDenyListBase;
import com.mcmoddev.orespawn.utils.codecs.BiomeMatcherConfig;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

public class BiomeMatcher extends AllowDenyListBase<RegistryKey<Registry<Biome>>> {
	private final BiomeMatcherConfig myConfig;

	public BiomeMatcher(final BiomeMatcherConfig config) {
		super( config.type, Registry.BIOME_KEY, config.data );
		myConfig = config;
	}

	public BiomeMatcherConfig getConfig() {
		return myConfig;
	}
}
