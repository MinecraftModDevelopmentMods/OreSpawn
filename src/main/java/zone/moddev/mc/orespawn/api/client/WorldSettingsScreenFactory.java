package zone.moddev.mc.orespawn.api.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Creates an add-on configuration screen whose Done action returns to OreSpawn's mod directory. */
@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface WorldSettingsScreenFactory {
	GuiScreen create(GuiScreen parent);
}
