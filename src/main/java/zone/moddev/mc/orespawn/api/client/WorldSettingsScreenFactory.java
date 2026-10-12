package zone.moddev.mc.orespawn.api.client;

import net.minecraft.client.gui.screens.Screen;

/** Creates an add-on configuration screen whose Done action returns to OreSpawn's mod directory. */
@FunctionalInterface
public interface WorldSettingsScreenFactory {
	Screen create(Screen parent);
}
