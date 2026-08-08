package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.CreateWorldScreen;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldCreationScreenHandler {
	private WorldCreationScreenHandler() {
	}

	@SubscribeEvent
	public static void onScreenInit(GuiScreenEvent.InitGuiEvent.Post event) {
		Screen screen = event.getGui();
		if (!(screen instanceof CreateWorldScreen)) {
			return;
		}

		WorldGeologyProfileManager.beginNewWorldCreation(screen);
		int width = 100;
		int x = Math.max(4, screen.width - width - 4);
		event.addWidget(new Button(x, 6, width, 20,
				new TranslationTextComponent("button.orespawn.world_settings"), button ->
						Minecraft.getInstance().setScreen(new OreSpawnWorldSettingsScreen(
								screen, WorldGeologyProfileManager.pendingNewWorldProfile(),
								DimensionDiscovery.availableDimensionIds((CreateWorldScreen) screen)))));
	}

	@SubscribeEvent
	public static void onScreenOpen(GuiOpenEvent event) {
		if (event.getGui() instanceof WorldSelectionScreen) {
			WorldGeologyProfileManager.clearPendingNewWorldProfile();
		}
	}
}
