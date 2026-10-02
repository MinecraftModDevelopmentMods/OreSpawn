package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;

/** Shared drawing and widget helpers for OreSpawn's Minecraft 1.13 screens. */
abstract class OreSpawnScreen extends GuiScreen {
	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
		for (GuiButton button : buttons) {
			if (button instanceof CompactScrollList) {
				((CompactScrollList) button).renderTooltip(mouseX, mouseY);
			}
		}
	}

	protected final ITextComponent title;
	protected final Minecraft minecraft = Minecraft.getInstance();
	protected FontRenderer font;

	OreSpawnScreen(ITextComponent title) {
		this.title = title;
	}

	@Override
	protected final void initGui() {
		font = mc.fontRenderer;
		init();
	}

	/** Initializes editor widgets after Minecraft has supplied the screen's font and size. */
	protected void init() {
	}

	/** Explicitly clears the previous frame before widgets and tooltips render. */
	protected final void renderBackground() {
		drawDefaultBackground();
	}

	public void onClose() {
		minecraft.displayGuiScreen(null);
	}

	@Override
	public final void close() {
		onClose();
	}

	protected final void drawCenteredString(FontRenderer renderer,
			ITextComponent text, int x, int y, int color) {
		super.drawCenteredString(renderer, text.getFormattedText(), x, y, color);
	}

	protected final void drawString(FontRenderer renderer,
			ITextComponent text, int x, int y, int color) {
		super.drawString(renderer, text.getFormattedText(), x, y, color);
	}

	protected final void renderComponentTooltip(List<? extends ITextComponent> lines,
			int mouseX, int mouseY) {
		List<String> text = new ArrayList<>();
		for (ITextComponent line : lines) text.add(line.getFormattedText());
		drawHoveringText(text, mouseX, mouseY);
	}

	final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		drawHoveringText(lines, mouseX, mouseY);
	}

	/** Gives the client integration test access to buttons without adding a public API. */
	final List<GuiButton> qualificationButtons() {
		return buttons;
	}
}
