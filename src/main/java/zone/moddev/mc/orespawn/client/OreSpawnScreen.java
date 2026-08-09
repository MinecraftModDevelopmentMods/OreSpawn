package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;

/**
 * Target-local rendering bridge. It preserves the later screen implementations
 * while dispatching through Minecraft 1.15's matrix-free Screen contract.
 */
abstract class OreSpawnScreen extends Screen {
	private static final MatrixStack EMPTY_STACK = new MatrixStack();
	protected final LegacyMinecraft minecraft = new LegacyMinecraft();

	OreSpawnScreen(ITextComponent title) {
		super(title);
	}

	@Override
	public final void render(int mouseX, int mouseY, float partialTick) {
		render(EMPTY_STACK, mouseX, mouseY, partialTick);
	}

	public void render(MatrixStack ignored, int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
	}

	protected final void renderBackground(MatrixStack ignored) {
		super.renderBackground();
	}

	protected final void drawCenteredString(MatrixStack ignored, FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		drawCenteredString(font, text.getFormattedText(), x, y, color);
	}

	protected final void drawCenteredString(MatrixStack ignored, FontRenderer font,
			String text, int x, int y, int color) {
		drawCenteredString(font, text, x, y, color);
	}

	protected final void drawString(MatrixStack ignored, FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		drawString(font, text.getFormattedText(), x, y, color);
	}

	protected final void drawString(MatrixStack ignored, FontRenderer font,
			String text, int x, int y, int color) {
		drawString(font, text, x, y, color);
	}

	protected final void renderComponentTooltip(MatrixStack ignored,
			List<? extends ITextComponent> lines, int mouseX, int mouseY) {
		List<String> text = new ArrayList<>();
		for (ITextComponent line : lines) text.add(line.getFormattedText());
		renderTooltip(text, mouseX, mouseY);
	}

	/** Keeps later screen navigation source local while calling the 1.15 client API. */
	protected static final class LegacyMinecraft {
		void setScreen(Screen screen) {
			net.minecraft.client.Minecraft.getInstance().displayGuiScreen(screen);
		}
	}
}
