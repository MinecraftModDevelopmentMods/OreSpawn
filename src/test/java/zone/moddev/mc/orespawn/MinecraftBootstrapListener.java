package zone.moddev.mc.orespawn;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
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
			SharedConstants.tryDetectVersion();
			Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
			bootstrapped.setAccessible(true);
			bootstrapped.setBoolean(null, true);
			LoadingModList.of(Collections.emptyList(), Collections.emptyList(),
					Collections.emptyList(), Collections.emptyList(),
					Collections.emptyMap());
			BuiltInRegistries.BLOCK.size();
			BuiltInRegistries.bootStrap();
			GameData.vanillaSnapshot();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not initialize Minecraft test registries", e);
		}
	}
}
