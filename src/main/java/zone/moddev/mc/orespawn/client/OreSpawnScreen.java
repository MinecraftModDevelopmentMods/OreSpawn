package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Renders Minecraft's background and widgets before custom foreground text.
 */
abstract class OreSpawnScreen extends Screen {
	protected OreSpawnScreen(Component title) {
		super(title);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderForeground(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void renderForeground(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick);
}
