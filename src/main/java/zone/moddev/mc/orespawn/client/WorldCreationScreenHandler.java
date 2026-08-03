package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import zone.moddev.mc.orespawn.OreSpawn;

@EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldCreationScreenHandler {
	private static final Logger LOGGER = LogManager.getLogger();
	// Minecraft 26.1 ships unobfuscated executables, so reflection must use the
	// actual target field names rather than the old runtime-obfuscated names.
	static final String TAB_MANAGER_FIELD = "tabManager";
	static final String TAB_NAVIGATION_BAR_FIELD = "tabNavigationBar";
	private WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (!(screen instanceof CreateWorldScreen)) {
			return;
		}

		CreateWorldScreen createWorldScreen = (CreateWorldScreen) screen;
		WorldGeologyProfileManager.beginNewWorldCreation(screen);
		installOreSpawnTab(event, createWorldScreen);
	}

	private static void installOreSpawnTab(ScreenEvent.Init.Post event, CreateWorldScreen screen) {
		TabNavigationBar original = findNavigationBar(event);
		if (original == null) {
			return;
		}

		List<Tab> tabs = new ArrayList<>();
		for (net.minecraft.client.gui.components.events.GuiEventListener child : original.children()) {
			if (child instanceof TabButton) {
				tabs.add(((TabButton) child).tab());
			}
		}
		if (tabs.isEmpty()) {
			return;
		}

		try {
			TabManager tabManager = readPrivate(screen, TAB_MANAGER_FIELD, TabManager.class);
			if (tabManager == null) {
				throw new IllegalStateException("CreateWorldScreen tab manager is unavailable");
			}
			boolean restoreOreSpawnTab = tabManager.getCurrentTab() instanceof OreSpawnWorldCreationTab;
			OreSpawnWorldCreationTab oreSpawnTab = new OreSpawnWorldCreationTab(screen,
					WorldGeologyProfileManager.pendingNewWorldProfile(),
					DimensionDiscovery.availableDimensionIds(screen));
			tabs.add(oreSpawnTab);
			TabNavigationBar replacement = TabNavigationBar.builder(tabManager, screen.width)
					.addTabs(tabs.toArray(new Tab[0]))
					.build();
			writePrivate(screen, TAB_NAVIGATION_BAR_FIELD, replacement);
			event.removeListener(original);
			event.addListener(replacement);
			if (restoreOreSpawnTab) {
				tabManager.setCurrentTab(oreSpawnTab, false);
			}
			screen.repositionElements();
		} catch (RuntimeException exception) {
			LOGGER.warn("Could not install the OreSpawn world-creation tab; using a fitted tab-row button",
					exception);
			installFallbackButton(event, screen, original);
		}
	}

	private static TabNavigationBar findNavigationBar(ScreenEvent.Init.Post event) {
		for (net.minecraft.client.gui.components.events.GuiEventListener listener : event.getListenersList()) {
			if (listener instanceof TabNavigationBar) {
				return (TabNavigationBar) listener;
			}
		}
		return null;
	}

	private static void installFallbackButton(ScreenEvent.Init.Post event, CreateWorldScreen screen,
			TabNavigationBar navigation) {
		List<AbstractWidget> nativeTabs = new ArrayList<>();
		for (net.minecraft.client.gui.components.events.GuiEventListener child : navigation.children()) {
			if (child instanceof AbstractWidget) {
				nativeTabs.add((AbstractWidget) child);
			}
		}
		if (nativeTabs.isEmpty()) {
			return;
		}

		int barWidth = Math.min(400, screen.width) - 28;
		int left = (screen.width - barWidth) / 2;
		int tabWidth = barWidth / (nativeTabs.size() + 1);
		for (int i = 0; i < nativeTabs.size(); i++) {
			AbstractWidget tab = nativeTabs.get(i);
			tab.setX(left + (i * tabWidth));
			tab.setWidth(tabWidth);
		}
		int x = left + (nativeTabs.size() * tabWidth);
		int width = left + barWidth - x;
		event.addListener(OreSpawnScreenLayout.plainButton(x, 0, width, 24,
				Component.translatable("button.orespawn.world_settings"),
				button -> openSettings(screen)));
	}

	private static void openSettings(CreateWorldScreen screen) {
		Minecraft.getInstance().setScreen(new OreSpawnWorldSettingsScreen(
				screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
				DimensionDiscovery.availableDimensionIds(screen)));
	}

	@SubscribeEvent
	public static void onScreenOpen(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof SelectWorldScreen) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}

	private static <T> T readPrivate(CreateWorldScreen screen, String name, Class<T> type) {
		try {
			Field field = CreateWorldScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(screen));
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot read CreateWorldScreen." + name, exception);
		}
	}

	private static void writePrivate(CreateWorldScreen screen, String name, Object value) {
		try {
			Field field = CreateWorldScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(screen, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Cannot write CreateWorldScreen." + name, exception);
		}
	}

}
