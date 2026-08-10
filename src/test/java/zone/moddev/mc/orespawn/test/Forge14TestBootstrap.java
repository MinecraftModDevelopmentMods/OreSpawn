package zone.moddev.mc.orespawn.test;

import net.minecraft.init.Bootstrap;

/** Initializes vanilla's static registries for isolated Forge 14 unit tests. */
public final class Forge14TestBootstrap {
	private static boolean initialized;

	private Forge14TestBootstrap() {
	}

	public static synchronized void registerVanilla() {
		if (initialized) return;
		Bootstrap.register();
		initialized = true;
	}
}
