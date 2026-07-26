package com.mcmoddev.orespawn.client;

import net.minecraft.network.chat.Component;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldCreationScreenHandler {
	private WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		Screen screen = event.getScreen();
		if (!(screen instanceof CreateWorldScreen)) {
			return;
		}

		WorldGeologyProfileManager.beginNewWorldCreation(screen);
		int width = 100;
		int x = Math.max(4, screen.width - width - 4);
		event.addListener(OreSpawnScreenLayout.plainButton(x, 6, width, 20,
				Component.translatable("button.orespawn.world_settings"), button ->
						Minecraft.getInstance().setScreen(new OreSpawnWorldSettingsScreen(
								screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
								DimensionDiscovery.availableDimensionIds((CreateWorldScreen) screen)))));
	}

	@SubscribeEvent
	public static void onScreenOpen(ScreenEvent.Opening event) {
		if (event.getNewScreen() instanceof SelectWorldScreen) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}
}
