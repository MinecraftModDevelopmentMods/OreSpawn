package zone.moddev.mc.orespawn.api.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Creates an add-on configuration screen whose Done action returns to OreSpawn's mod directory. */
@FunctionalInterface
@SideOnly(Side.CLIENT)
public interface WorldSettingsScreenFactory {
	GuiScreen create(GuiScreen parent);
}
