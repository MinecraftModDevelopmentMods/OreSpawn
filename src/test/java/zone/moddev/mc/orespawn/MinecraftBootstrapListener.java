package zone.moddev.mc.orespawn;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import cpw.mods.modlauncher.api.IModuleLayerManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;

/**
 * Opens vanilla registry construction for headless unit tests without invoking
 * Forge's ModLauncher-only networking bootstrap.
 */
public final class MinecraftBootstrapListener implements LauncherSessionListener {
	@Override
	public void launcherSessionOpened(LauncherSession session) {
		try {
			SharedConstants.tryDetectVersion();
			Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
			bootstrapped.setAccessible(true);
			bootstrapped.setBoolean(null, true);
			Class<?> stateType = Class.forName("net.minecraftforge.fml.loading.ModSorter$State");
			var stateConstructor = stateType.getDeclaredConstructor(java.util.List.class,
					java.util.List.class);
			stateConstructor.setAccessible(true);
			Object emptyState = stateConstructor.newInstance(Collections.emptyList(),
					Collections.emptyList());
			Class<?> loadingModListType = Class.forName(
					"net.minecraftforge.fml.loading.LoadingModListImpl");
			Field pendingState = loadingModListType.getDeclaredField("temp");
			pendingState.setAccessible(true);
			pendingState.set(null, emptyState);
			LoadingModList.getMods();
			pendingState.set(null, null);
			Field moduleLayerManager = FMLLoader.class.getDeclaredField("moduleLayerManager");
			moduleLayerManager.setAccessible(true);
			IModuleLayerManager testLayers =
					layer -> Optional.of(ModuleLayer.boot());
			moduleLayerManager.set(null, testLayers);
			BuiltInRegistries.BLOCK.size();
			BuiltInRegistries.bootStrap();
			GameData.vanillaSnapshot();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not initialize Minecraft test registries", e);
		}
	}
}
