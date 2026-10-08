package zone.moddev.mc.orespawn.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

/** Small bridge for editor controls drawn by Minecraft's pose-stack API. */
class Button extends net.minecraft.client.gui.components.Button {
	private final Tooltip tooltip;
	private PoseStack currentPoseStack;

	Button(int x, int y, int width, int height, String message, OnPress onPress) {
		this(x, y, width, height, new TextComponent(message), onPress, null);
	}

	Button(int x, int y, int width, int height, Component message, OnPress onPress) {
		this(x, y, width, height, message, onPress, null);
	}

	Button(int x, int y, int width, int height, String message, OnPress onPress,
			Tooltip tooltip) {
		this(x, y, width, height, new TextComponent(message), onPress, tooltip);
	}

	Button(int x, int y, int width, int height, Component message, OnPress onPress,
			Tooltip tooltip) {
		super(x, y, width, height, message, onPress);
		this.tooltip = tooltip;
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
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		super.renderButton(poseStack, mouseX, mouseY, partialTick);
		if (tooltip != null && isHoveredOrFocused()) tooltip.render(this, mouseX, mouseY);
	}

	protected final PoseStack currentPoseStack() {
		return currentPoseStack;
	}

	protected final void fill(int x1, int y1, int x2, int y2, int color) {
		GuiComponent.fill(currentPoseStack, x1, y1, x2, y2, color);
	}

	protected final void drawCenteredString(Font font, String text,
			int x, int y, int color) {
		GuiComponent.drawCenteredString(currentPoseStack, font, text, x, y, color);
	}

	interface Tooltip {
		void render(Button button, int mouseX, int mouseY);
	}
}
