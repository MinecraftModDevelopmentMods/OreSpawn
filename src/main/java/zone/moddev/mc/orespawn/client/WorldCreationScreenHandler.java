package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ScreenOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldCreationScreenHandler {
	private WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
		Screen screen = event.getScreen();
		if (!(screen instanceof CreateWorldScreen)) {
			return;
		}

		WorldGeologyProfileManager.beginNewWorldCreation(screen);
		int width = 100;
		int x = Math.max(4, screen.width - width - 4);
		event.addListener(new Button(x, 6, width, 20,
				new TranslatableComponent("button.orespawn.world_settings"), button ->
						Minecraft.getInstance().setScreen(new OreSpawnWorldSettingsScreen(
								screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
								DimensionDiscovery.availableDimensionIds((CreateWorldScreen) screen)))));
	}

	@SubscribeEvent
	public static void onScreenOpen(ScreenOpenEvent event) {
		if (event.getScreen() instanceof SelectWorldScreen) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}
}
