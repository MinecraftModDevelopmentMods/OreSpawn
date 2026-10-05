package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.ITextComponent;

/**
 * Shared Minecraft 1.15 screen helpers. Concrete screens implement the native
 * matrix-free {@link Screen#render(int, int, float)} contract directly.
 */
abstract class OreSpawnScreen extends Screen {
	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
		for (Widget widget : buttons) {
			if (widget instanceof CompactScrollList) {
				((CompactScrollList) widget).renderTooltip(mouseX, mouseY);
			}
		}
	}

	OreSpawnScreen(ITextComponent title) {
		super(title);
	}

	protected final void drawCenteredString(FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		super.drawCenteredString(font, text.getFormattedText(), x, y, color);
	}

	protected final void drawString(FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		super.drawString(font, text.getFormattedText(), x, y, color);
	}

	protected final void renderComponentTooltip(List<? extends ITextComponent> lines,
			int mouseX, int mouseY) {
		List<String> text = new ArrayList<>();
		for (ITextComponent line : lines) text.add(line.getFormattedText());
		renderTooltip(text, mouseX, mouseY);
	}

	final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		renderTooltip(lines, mouseX, mouseY);
	}

	/** Gives the isolated client test access to the screen's buttons. */
	final List<Widget> qualificationButtons() {
		return buttons;
	}
}
