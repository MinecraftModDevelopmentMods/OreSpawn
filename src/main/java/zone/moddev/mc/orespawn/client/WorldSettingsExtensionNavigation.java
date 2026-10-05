package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.OreSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/** Keeps an add-on's inherited Escape action inside OreSpawn's screen stack. */
@Mod.EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldSettingsExtensionNavigation {
	private static Screen directory;
	private static Screen extensionScreen;

	private WorldSettingsExtensionNavigation() {
	}

	static synchronized void open(Screen parentDirectory, Screen screen) {
		directory = parentDirectory;
		extensionScreen = screen;
		Minecraft.getInstance().displayGuiScreen(screen);
	}

	@SubscribeEvent
	public static synchronized void onScreenOpen(GuiOpenEvent event) {
		if (extensionScreen == null) return;
		// Allow the initial jump from the directory to the add-on's screen.
		if (event.getGui() == extensionScreen) return;
		Minecraft minecraft = Minecraft.getInstance();
		boolean inheritedEscape = event.getGui() == null
				|| (minecraft.world == null && event.getGui() instanceof MainMenuScreen);
		if (minecraft.currentScreen == extensionScreen && inheritedEscape) {
			event.setGui(directory);
		}
		// Done already returns to the directory. Leave the add-on's other screen transitions alone.
		directory = null;
		extensionScreen = null;
	}
}
