package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Keeps the compact editor controls on Minecraft's native drawing API. */
class Button extends net.minecraft.client.gui.components.Button {
	private final Tooltip tooltip;
	private GuiGraphicsExtractor currentGraphics;

	Button(int x, int y, int width, int height, String message, OnPress onPress) {
		this(x, y, width, height, Component.literal(message), onPress, null);
	}

	Button(int x, int y, int width, int height, Component message, OnPress onPress) {
		this(x, y, width, height, message, onPress, null);
	}

	Button(int x, int y, int width, int height, String message, OnPress onPress,
			Tooltip tooltip) {
		this(x, y, width, height, Component.literal(message), onPress, tooltip);
	}

	Button(int x, int y, int width, int height, Component message, OnPress onPress,
			Tooltip tooltip) {
		super(x, y, width, height, message, onPress,
				net.minecraft.client.gui.components.Button.DEFAULT_NARRATION);
		this.tooltip = tooltip;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		currentGraphics = graphics;
		try {
			if (!(this instanceof CompactScrollList)) extractDefaultSprite(graphics);
			render(mouseX, mouseY, partialTick);
		} finally {
			currentGraphics = null;
		}
	}

	public void render(int mouseX, int mouseY, float partialTick) {
		currentGraphics.centeredText(Minecraft.getInstance().font, getMessage(),
				getX() + width / 2, getY() + (height - 8) / 2,
				active ? 0xFFFFFFFF : 0xFFA0A0A0);
		if (tooltip != null && isHoveredOrFocused()) tooltip.render(this, mouseX, mouseY);
	}

	protected final GuiGraphicsExtractor currentGraphics() {
		return currentGraphics;
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		currentGraphics.fill(x1, y1, x2, y2, color);
	}

	protected final void drawCenteredString(Font font, String text,
			int x, int y, int color) {
		currentGraphics.centeredText(font, text, x, y, color | 0xFF000000);
	}

	interface Tooltip {
		void render(Button button, int mouseX, int mouseY);
	}
}
