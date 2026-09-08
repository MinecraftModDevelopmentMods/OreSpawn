package zone.moddev.mc.orespawn.test;

import net.minecraft.init.Bootstrap;

/** Initializes vanilla's static registries for isolated Forge 13 unit tests. */
public final class Forge13TestBootstrap {
	private static boolean initialized;

	private Forge13TestBootstrap() {
	}

	public static synchronized void registerVanilla() {
		if (initialized) return;
		Bootstrap.register();
		initialized = true;
	}
}
