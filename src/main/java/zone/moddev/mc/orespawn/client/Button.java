package zone.moddev.mc.orespawn.client;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Small bridge for editor controls drawn by Minecraft's 1.16 matrix API. */
class Button extends net.minecraft.client.gui.widget.button.Button {
	private final Tooltip tooltip;
	private MatrixStack currentPoseStack;

	Button(int x, int y, int width, int height, String message, IPressable onPress) {
		this(x, y, width, height, new StringTextComponent(message), onPress, null);
	}

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress) {
		this(x, y, width, height, message, onPress, null);
	}

	Button(int x, int y, int width, int height, String message, IPressable onPress,
			Tooltip tooltip) {
		this(x, y, width, height, new StringTextComponent(message), onPress, tooltip);
	}

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress,
			Tooltip tooltip) {
		super(x, y, width, height, message, onPress);
		this.tooltip = tooltip;
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
	}

	@Override
	public void renderButton(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		super.renderButton(poseStack, mouseX, mouseY, partialTick);
		if (tooltip != null && isHovered()) tooltip.render(this, mouseX, mouseY);
	}

	protected final MatrixStack currentPoseStack() {
		return currentPoseStack;
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		AbstractGui.fill(currentPoseStack, x1, y1, x2, y2, color);
	}

	protected final void drawCenteredString(FontRenderer font, String text,
			int x, int y, int color) {
		AbstractGui.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	interface Tooltip {
		void render(Button button, int mouseX, int mouseY);
	}
}
