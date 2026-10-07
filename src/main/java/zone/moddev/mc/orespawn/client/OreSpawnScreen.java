package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/**
 * Lets the compact editor screens share their drawing code while Minecraft
 * 1.17 supplies the pose stack for each frame.
 */
abstract class OreSpawnScreen extends Screen {
	private PoseStack currentPoseStack;

	OreSpawnScreen(Component title) {
		super(title);
	}

	protected final <T extends AbstractWidget> T addButton(T widget) {
		return addRenderableWidget(widget);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		currentPoseStack = poseStack;
		try {
			render(mouseX, mouseY, partialTick);
		} finally {
			currentPoseStack = null;
		}
	}

	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(currentPoseStack, mouseX, mouseY, partialTick);
		for (net.minecraft.client.gui.components.Widget widget : renderables) {
			if (widget instanceof CompactScrollList) {
				((CompactScrollList) widget).renderTooltip(mouseX, mouseY);
			}
		}
	}

	protected final void renderBackground() {
		super.renderBackground(currentPoseStack);
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		GuiComponent.fill(currentPoseStack, x1, y1, x2, y2, color);
	}

	protected final void drawCenteredString(Font font,
			Component text, int x, int y, int color) {
		super.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawCenteredString(Font font,
			String text, int x, int y, int color) {
		super.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawString(Font font,
			Component text, int x, int y, int color) {
		super.drawString(currentPoseStack, font, text, x, y, color);
	}

	protected final void drawString(Font font,
			String text, int x, int y, int color) {
		super.drawString(currentPoseStack, font, text, x, y, color);
	}

	protected final void renderComponentTooltip(List<? extends Component> lines,
			int mouseX, int mouseY) {
		List<Component> copy = new ArrayList<>(lines);
		super.renderComponentTooltip(currentPoseStack, copy, mouseX, mouseY);
	}

	final void renderStringTooltip(List<String> lines, int mouseX, int mouseY) {
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		for (String line : lines) {
			wrapped.addAll(font.split(new TextComponent(line), Math.max(80, width - 24)));
		}
		super.renderTooltip(currentPoseStack, wrapped, mouseX, mouseY);
	}

	final PoseStack currentPoseStack() {
		return currentPoseStack;
	}

	final List<net.minecraft.client.gui.components.Widget> qualificationButtons() {
		return new ArrayList<>(renderables);
	}
}
