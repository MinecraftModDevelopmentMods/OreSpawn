package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class WorldCreationScreenHandler {
	WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
		GuiScreen screen = event.getGui();
		if (!(screen instanceof GuiCreateWorld)) {
			return;
		}

		WorldGeologyProfileManager.beginNewWorldCreation(screen);
		int width = 100;
		int x = Math.max(4, screen.width - width - 4);
		event.getButtonList().add(new Button(x, 6, width, 20,
				new TextComponentTranslation("button.orespawn.world_settings"), button ->
						Minecraft.getMinecraft().displayGuiScreen(new OreSpawnWorldSettingsScreen(
								screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
								DimensionDiscovery.availableDimensionIds((GuiCreateWorld) screen)))));
	}

	@SubscribeEvent
	public static void onScreenOpen(GuiOpenEvent event) {
		if (event.getGui() instanceof GuiWorldSelection) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}
}
