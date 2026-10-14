package zone.moddev.mc.orespawn.test;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

/** Initializes vanilla registries for tests that inspect biome definitions. */
public final class Forge36TestBootstrap {
	private Forge36TestBootstrap() { }

	public static void registerVanilla() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
}
