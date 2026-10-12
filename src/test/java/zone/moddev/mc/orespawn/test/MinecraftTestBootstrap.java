package zone.moddev.mc.orespawn.test;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

/** Initializes vanilla registries for tests that inspect biome definitions. */
public final class MinecraftTestBootstrap {
	private MinecraftTestBootstrap() { }

	public static void registerVanilla() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}
}
