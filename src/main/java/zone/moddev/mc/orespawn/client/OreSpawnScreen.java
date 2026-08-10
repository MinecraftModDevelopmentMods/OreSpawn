package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

/** Shared target-native helpers for the Minecraft 1.12 screen contract. */
abstract class OreSpawnScreen extends GuiScreen {
	protected final ITextComponent title;
	protected final Minecraft minecraft = Minecraft.getMinecraft();
	protected FontRenderer font;
	/** Later-name aliases retained only inside OreSpawn's own UI implementation. */
	protected final List<GuiButton> buttons = buttonList;
	protected final List<Object> children = new ArrayList<>();

	OreSpawnScreen(ITextComponent title) {
		this.title = title;
	}

	@Override
	public final void initGui() {
		font = fontRenderer;
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

	public final void close() {
		onClose();
	}

	@Override
	public void onGuiClosed() {
		onClose();
	}

	@Override
	public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
		render(mouseX, mouseY, partialTicks);
	}

	/** Later-name adapter used by the 24 target-native editor implementations. */
	public void render(int mouseX, int mouseY, float partialTicks) {
		super.drawScreen(mouseX, mouseY, partialTicks);
		for (GuiButton widget : buttonList) {
			if (widget instanceof Button) ((Button) widget).renderTooltip(mouseX, mouseY);
		}
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button instanceof Button) ((Button) button).press();
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
		for (GuiButton widget : buttonList) {
			if (widget instanceof TextFieldWidget
					&& ((TextFieldWidget) widget).keyTyped(typedChar, keyCode)) return;
		}
		super.keyTyped(typedChar, keyCode);
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
		drawHoveringText(lines, mouseX, mouseY, fontRenderer);
	}
}
