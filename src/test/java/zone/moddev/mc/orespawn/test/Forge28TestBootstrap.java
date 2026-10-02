package zone.moddev.mc.orespawn.test;

import java.lang.reflect.Field;

import net.minecraft.util.registry.Bootstrap;

/** Supplies the Forge 28 version fields normally set by its launcher. */
public final class Forge28TestBootstrap {
	private static boolean initialized;

	private Forge28TestBootstrap() { }

	public static synchronized void registerVanilla() {
		if (initialized) return;
		try {
			Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
			set(loader, "mcVersion", "1.14.4");
			set(loader, "mcpVersion", "20190829.143755");
			set(loader, "forgeVersion", "28.2.26");
			set(loader, "forgeGroup", "net.minecraftforge");
			Bootstrap.register();
			initialized = true;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Unable to initialize the Forge 28 test runtime", ex);
		}
	}

	private static void set(Class<?> owner, String name, String value)
			throws ReflectiveOperationException {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
