package zone.moddev.mc.orespawn.worldgen;

import net.neoforged.neoforge.registries.RegisterEvent;

/** Internal bootstrap for biome-source codecs used by saved generator data. */
public final class BiomeWorldgenBootstrap {
	private BiomeWorldgenBootstrap() {
	}

	public static void registerCodecs(RegisterEvent event) {
		BiomeWorldgenManager.registerBiomeSourceCodec(event);
	}
}
