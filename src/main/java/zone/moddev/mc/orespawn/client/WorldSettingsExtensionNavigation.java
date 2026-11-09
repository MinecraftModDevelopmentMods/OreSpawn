package zone.moddev.mc.orespawn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Keeps an add-on's inherited Escape action inside OreSpawn's screen stack. */
public final class WorldSettingsExtensionNavigation {
	private static GuiScreen directory;
	private static GuiScreen extensionScreen;

	private WorldSettingsExtensionNavigation() {
	}

	static synchronized void open(GuiScreen parentDirectory, GuiScreen screen) {
		directory = parentDirectory;
		extensionScreen = screen;
		Minecraft.getMinecraft().displayGuiScreen(screen);
	}

	@SubscribeEvent
	public static synchronized void onScreenOpen(GuiOpenEvent event) {
		if (extensionScreen == null) return;
		// This is the initial transition from the directory to the add-on.
		if (event.getGui() == extensionScreen) return;
		Minecraft minecraft = Minecraft.getMinecraft();
		boolean inheritedEscape = event.getGui() == null
				|| (minecraft.world == null && event.getGui() instanceof GuiMainMenu);
		if (minecraft.currentScreen == extensionScreen && inheritedEscape) {
			event.setGui(directory);
		}
		// Done already names the directory as its destination. Other non-null
		// transitions belong to the add-on and are deliberately left untouched.
		directory = null;
		extensionScreen = null;
	}
}
