package zone.moddev.mc.orespawn.client;

import net.minecraftforge.common.MinecraftForge;

/** Client-only registration invoked from Forge 1.10 pre-initialization. */
public final class ClientSetup {
	private static boolean initialized;

	private ClientSetup() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		// Forge 1.10 ignores static @SubscribeEvent methods when their declaring
		// class is registered as an instance. Both world-creation hooks are static,
		// so the class object is required for the OreSpawn button to be installed.
		MinecraftForge.EVENT_BUS.register(WorldCreationScreenHandler.class);
	}
}
