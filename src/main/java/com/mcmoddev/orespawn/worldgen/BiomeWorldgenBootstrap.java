package com.mcmoddev.orespawn.worldgen;

/** Internal bootstrap for biome-source codecs used by saved generator data. */
public final class BiomeWorldgenBootstrap {
	private BiomeWorldgenBootstrap() {
	}

	public static void registerCodecs() {
		BiomeWorldgenManager.registerBiomeSourceCodec();
	}
}
