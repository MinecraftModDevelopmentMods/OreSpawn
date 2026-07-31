package zone.moddev.mc.orespawn;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.Collections;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.GameData;

/**
 * Opens vanilla registry construction for headless unit tests without invoking
 * NeoForge's ModLauncher-only networking bootstrap.
 */
public final class MinecraftBootstrapListener implements LauncherSessionListener {
	@Override
	public void launcherSessionOpened(LauncherSession session) {
		try {
			FMLLoader loader = FMLLoader.getCurrentOrNull();
			if (loader == null) {
				Constructor<FMLLoader> constructor = FMLLoader.class.getDeclaredConstructor(
						ClassLoader.class, String[].class, Dist.class, boolean.class, Path.class);
				constructor.setAccessible(true);
				loader = constructor.newInstance(
						ClassLoader.getSystemClassLoader(), new String[0], Dist.CLIENT, false,
						Path.of(".").toAbsolutePath().normalize());
			}
			LoadingModList loadingModList = LoadingModList.of(
					Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
					Collections.emptyList(), Collections.emptyList(), Collections.emptyMap());
			Field loadingModListField = FMLLoader.class.getDeclaredField("loadingModList");
			loadingModListField.setAccessible(true);
			loadingModListField.set(loader, loadingModList);
			SharedConstants.tryDetectVersion();
			Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
			bootstrapped.setAccessible(true);
			bootstrapped.setBoolean(null, true);
			BuiltInRegistries.BLOCK.size();
			BuiltInRegistries.bootStrap();
			GameData.vanillaSnapshot();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not initialize Minecraft test registries", e);
		}
	}
}
