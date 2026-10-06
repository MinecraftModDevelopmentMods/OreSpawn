package zone.moddev.mc.orespawn.test;

import net.minecraft.util.registry.Bootstrap;

/** Initializes vanilla registries for tests that inspect biome definitions. */
public final class Forge36TestBootstrap {
	private Forge36TestBootstrap() { }

	public static void registerVanilla() {
		Bootstrap.bootStrap();
	}
}
