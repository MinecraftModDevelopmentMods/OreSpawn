package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.OreSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenOpenEvent;
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
		Minecraft.getInstance().setScreen(screen);
	}

	@SubscribeEvent
	public static synchronized void onScreenOpen(ScreenOpenEvent event) {
		if (extensionScreen == null) return;
		// This is the initial transition from the directory to the add-on.
		if (event.getScreen() == extensionScreen) return;
		Minecraft minecraft = Minecraft.getInstance();
		boolean inheritedEscape = event.getScreen() == null
				|| (minecraft.level == null && event.getScreen() instanceof TitleScreen);
		if (minecraft.screen == extensionScreen && inheritedEscape) {
			event.setScreen(directory);
		}
		// Done already names the directory as its destination. Other non-null
		// transitions belong to the add-on and are deliberately left untouched.
		directory = null;
		extensionScreen = null;
	}
}
