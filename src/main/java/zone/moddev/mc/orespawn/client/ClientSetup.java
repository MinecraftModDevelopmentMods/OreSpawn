package zone.moddev.mc.orespawn.client;

import net.minecraftforge.common.MinecraftForge;

/** Client-only registration invoked from Forge 1.12 pre-initialization. */
public final class ClientSetup {
	private static boolean initialized;

	private ClientSetup() {
	}

	public static synchronized void initialize() {
		if (initialized) return;
		initialized = true;
		MinecraftForge.EVENT_BUS.register(new WorldCreationScreenHandler());
	}
}
