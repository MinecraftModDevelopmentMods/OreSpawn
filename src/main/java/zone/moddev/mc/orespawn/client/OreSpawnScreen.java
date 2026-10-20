package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Renders Minecraft's background and widgets before custom foreground text.
 */
abstract class OreSpawnScreen extends Screen {
	private GuiGraphics currentGraphics;

	protected OreSpawnScreen(Component title) {
		super(title);
	}

	@Override
	public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		currentGraphics = graphics;
		try {
			super.render(graphics, mouseX, mouseY, partialTick);
			renderForeground(graphics, mouseX, mouseY, partialTick);
			for (var child : children()) {
				if (child instanceof CompactScrollList list) list.renderTooltip(mouseX, mouseY);
			}
		} finally {
			currentGraphics = null;
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick) {
		GuiGraphics previous = currentGraphics;
		currentGraphics = graphics;
		try {
			super.renderBackground(graphics, mouseX, mouseY, partialTick);
			renderBeforeWidgets(graphics);
		} finally {
			currentGraphics = previous;
		}
	}

	protected final <T extends AbstractWidget> T addButton(T widget) {
		return addRenderableWidget(widget);
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		currentGraphics.fill(x1, y1, x2, y2, color);
	}

	protected final void drawString(Font font, Component text, int x, int y, int color) {
		currentGraphics.drawString(font, text, x, y, color);
	}

	protected final void drawCenteredString(Font font, Component text, int x, int y, int color) {
		currentGraphics.drawCenteredString(font, text, x, y, color);
	}

	protected final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		List<Component> text = new ArrayList<>(lines.size());
		for (String line : lines) text.add(Component.literal(line));
		currentGraphics.renderComponentTooltip(font, text, mouseX, mouseY, ItemStack.EMPTY);
	}

	protected final GuiGraphics currentGraphics() {
		return currentGraphics;
	}

	protected abstract void renderForeground(GuiGraphics graphics, int mouseX, int mouseY,
			float partialTick);

	protected void renderBeforeWidgets(GuiGraphics graphics) { }
}
