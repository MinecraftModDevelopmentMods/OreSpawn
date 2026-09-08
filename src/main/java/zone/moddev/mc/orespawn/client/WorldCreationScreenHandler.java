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
	// GuiCreateWorld reserves IDs 0-7; ID 0 creates the world immediately.
	private static final int WORLD_SETTINGS_BUTTON_ID = 0x4F53;

	private WorldCreationScreenHandler() {
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
		event.getButtonList().add(new Button(WORLD_SETTINGS_BUTTON_ID, x, 6, width, 20,
				new TextComponentTranslation("button.orespawn.world_settings"), button ->
						Minecraft.getMinecraft().displayGuiScreen(new OreSpawnWorldSettingsScreen(
								screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
								DimensionDiscovery.availableDimensionIds((GuiCreateWorld) screen)))));
	}

	@SubscribeEvent
	public static void onButtonPressed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
		if (!(event.getGui() instanceof GuiCreateWorld)
				|| !(event.getButton() instanceof Button)
				|| event.getButton().id != WORLD_SETTINGS_BUTTON_ID) {
			return;
		}

		// Forge posts this before GuiCreateWorld.actionPerformed. Canceling keeps
		// vanilla from interpreting the button ID while OreSpawn runs its callback.
		event.setCanceled(true);
		((Button) event.getButton()).press();
	}

	@SubscribeEvent
	public static void onScreenOpen(GuiOpenEvent event) {
		if (event.getGui() instanceof GuiWorldSelection) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}
}
