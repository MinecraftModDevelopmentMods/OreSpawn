package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.OreSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

/** Keeps an add-on's inherited Escape action inside OreSpawn's screen stack. */
@EventBusSubscriber(modid = OreSpawn.MODID, value = Dist.CLIENT)
public final class WorldSettingsExtensionNavigation {
	private static Screen directory;
	private static Screen extensionScreen;

	private WorldSettingsExtensionNavigation() {
	}

	static synchronized void open(Screen parentDirectory, Screen screen) {
		directory = parentDirectory;
		extensionScreen = screen;
		Minecraft.getInstance().gui.setScreen(screen);
	}

	@SubscribeEvent
	public static synchronized void onScreenOpen(ScreenEvent.Opening event) {
		if (extensionScreen == null) return;
		// This is the initial transition from the directory to the add-on.
		if (event.getNewScreen() == extensionScreen) return;
		Minecraft minecraft = Minecraft.getInstance();
		boolean inheritedEscape = event.getNewScreen() == null
				|| (minecraft.level == null && event.getNewScreen() instanceof TitleScreen);
		if (minecraft.gui.screen() == extensionScreen && inheritedEscape) {
			event.setNewScreen(directory);
		}
		// Done already names the directory as its destination. Other non-null
		// transitions belong to the add-on and are deliberately left untouched.
		directory = null;
		extensionScreen = null;
	}
}
