package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;

/** Shared target-native helpers for the Minecraft 1.13 screen contract. */
abstract class OreSpawnScreen extends GuiScreen {
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

	/** Later-screen-style initialization retained internally for the editors. */
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
}
