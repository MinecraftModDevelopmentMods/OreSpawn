package zone.moddev.mc.orespawn.worldgen;

/** Keeps the shared biome-source startup hook; Minecraft 1.13 needs no codec registration. */
public final class BiomeWorldgenBootstrap {
	private BiomeWorldgenBootstrap() {
	}

	public static void registerCodecs() {
		BiomeWorldgenManager.registerBiomeSourceCodec();
	}
}
