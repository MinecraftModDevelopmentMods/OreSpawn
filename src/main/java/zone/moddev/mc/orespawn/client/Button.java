package zone.moddev.mc.orespawn.client;

import net.minecraft.util.text.ITextComponent;

/** 1.15 string-widget bridge used by the target-local editor screens. */
class Button extends net.minecraft.client.gui.widget.button.Button {
	private static final com.mojang.blaze3d.matrix.MatrixStack EMPTY_STACK =
			new com.mojang.blaze3d.matrix.MatrixStack();
	private final Tooltip tooltip;

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress) {
		this(x, y, width, height, message.getFormattedText(), onPress);
	}

	Button(int x, int y, int width, int height, String message, IPressable onPress) {
		super(x, y, width, height, message, onPress);
		this.tooltip = null;
	}

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress,
			Tooltip tooltip) {
		this(x, y, width, height, message.getFormattedText(), onPress, tooltip);
	}

	Button(int x, int y, int width, int height, String message, IPressable onPress,
			Tooltip tooltip) {
		super(x, y, width, height, message, onPress);
		this.tooltip = tooltip;
	}

	@Override
	public void renderButton(int mouseX, int mouseY, float partialTick) {
		super.renderButton(mouseX, mouseY, partialTick);
		if (tooltip != null && isHovered()) {
			tooltip.render(this, EMPTY_STACK, mouseX, mouseY);
		}
	}

	interface Tooltip {
		void render(Button button, com.mojang.blaze3d.matrix.MatrixStack stack, int mouseX, int mouseY);
	}
}
