package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
	public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		renderForeground(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick);
}
