package zone.moddev.mc.orespawn.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Renders Minecraft's background and widgets before custom foreground text.
 */
abstract class OreSpawnScreen extends Screen {
	private GuiGraphicsExtractor currentGraphics;

	protected OreSpawnScreen(Component title) {
		super(title);
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		currentGraphics = graphics;
		try {
			renderBeforeWidgets(graphics);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			renderForeground(graphics, mouseX, mouseY, partialTick);
			for (var child : children()) {
				if (child instanceof CompactScrollList list) list.renderTooltip(mouseX, mouseY);
			}
		} finally {
			currentGraphics = null;
		}
	}

	protected final <T extends AbstractWidget> T addButton(T widget) {
		return addRenderableWidget(widget);
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		currentGraphics.fill(x1, y1, x2, y2, color);
	}

	protected final void drawString(Font font, Component text, int x, int y, int color) {
		currentGraphics.text(font, text, x, y, color | 0xFF000000);
	}

	protected final void drawCenteredString(Font font, Component text, int x, int y, int color) {
		currentGraphics.centeredText(font, text, x, y, color | 0xFF000000);
	}

	protected final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		currentGraphics.setTooltipForNextFrame(font,
				lines.stream().map(Component::literal).map(Component::getVisualOrderText).toList(),
				mouseX, mouseY);
	}

	protected final GuiGraphicsExtractor currentGraphics() {
		return currentGraphics;
	}

	protected abstract void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
			float partialTick);

	protected void renderBeforeWidgets(GuiGraphicsExtractor graphics) { }
}
