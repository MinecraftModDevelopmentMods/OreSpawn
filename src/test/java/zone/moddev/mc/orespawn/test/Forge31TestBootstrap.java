package zone.moddev.mc.orespawn.test;

import java.lang.reflect.Field;

import net.minecraft.util.registry.Bootstrap;

/** Supplies launcher version fields for plain unit tests that load biomes. */
public final class Forge31TestBootstrap {
	private static boolean initialized;

	private Forge31TestBootstrap() { }

	public static synchronized void registerVanilla() {
		if (initialized) return;
		try {
			Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
			set(loader, "mcVersion", "1.15.2");
			set(loader, "mcpVersion", "20200515.085601");
			set(loader, "forgeVersion", "31.2.57");
			set(loader, "forgeGroup", "net.minecraftforge");
			Bootstrap.register();
			initialized = true;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to initialize the Forge 31 test runtime", ex);
		}
	}

	private static void set(Class<?> owner, String name, String value)
			throws ReflectiveOperationException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
