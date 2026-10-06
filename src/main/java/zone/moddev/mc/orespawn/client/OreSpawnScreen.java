package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/**
 * Lets the compact editor screens share their drawing code while Minecraft
 * 1.16 supplies the matrix stack for each frame.
 */
abstract class OreSpawnScreen extends Screen {
	private MatrixStack currentPoseStack;

	OreSpawnScreen(ITextComponent title) {
		super(title);
	}

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		currentPoseStack = poseStack;
		try {
			render(mouseX, mouseY, partialTick);
		} finally {
			currentPoseStack = null;
		}
	}

	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(currentPoseStack, mouseX, mouseY, partialTick);
		for (Widget widget : buttons) {
			if (widget instanceof CompactScrollList) {
				((CompactScrollList) widget).renderTooltip(mouseX, mouseY);
			}
		}
	}

	protected final void renderBackground() {
		super.renderBackground(currentPoseStack);
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		AbstractGui.fill(currentPoseStack, x1, y1, x2, y2, color);
	}

	protected final void drawCenteredString(FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		super.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawCenteredString(FontRenderer font,
			String text, int x, int y, int color) {
		super.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawString(FontRenderer font,
			ITextComponent text, int x, int y, int color) {
		super.drawString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawString(FontRenderer font,
			String text, int x, int y, int color) {
		super.drawString(currentPoseStack, font, text, x, y, color);
	}

	protected final void renderComponentTooltip(List<? extends ITextComponent> lines,
			int mouseX, int mouseY) {
		List<ITextComponent> copy = new ArrayList<>(lines);
		super.renderComponentTooltip(currentPoseStack, copy, mouseX, mouseY);
	}

	final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		List<IReorderingProcessor> wrapped = new ArrayList<>();
		for (String line : lines) {
			wrapped.addAll(font.split(new StringTextComponent(line), Math.max(80, width - 24)));
		}
		super.renderTooltip(currentPoseStack, wrapped, mouseX, mouseY);
	}

	final MatrixStack currentPoseStack() {
		return currentPoseStack;
	}

	final List<Widget> qualificationButtons() {
		return new ArrayList<Widget>(buttons);
	}
}
