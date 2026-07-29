package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess;
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
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldCreationScreenHandler {
	private static final Logger LOGGER = LogManager.getLogger();

	private WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (!(screen instanceof CreateWorldScreen)) {
			return;
		}

		CreateWorldScreen createWorldScreen = (CreateWorldScreen) screen;
		BiomeRegistryAccess.bind(createWorldScreen.getUiState().getSettings().worldgenLoadContext());
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
			TabManager tabManager = screen.tabManager;
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
			screen.tabNavigationBar = replacement;
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

}
