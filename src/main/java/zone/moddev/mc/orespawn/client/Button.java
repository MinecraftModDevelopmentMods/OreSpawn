package zone.moddev.mc.orespawn.client;

import net.minecraft.util.text.ITextComponent;

/** Target-native string button with OreSpawn's callback and tooltip contract. */
class Button extends net.minecraft.client.gui.GuiButton {
	private final IPressable onPress;
	private final Tooltip tooltip;

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress) {
		this(x, y, width, height, message.getFormattedText(), onPress);
	}

	Button(int x, int y, int width, int height, String message, IPressable onPress) {
		this(x, y, width, height, message, onPress, null);
	}

	Button(int x, int y, int width, int height, ITextComponent message, IPressable onPress,
			Tooltip tooltip) {
		this(x, y, width, height, message.getFormattedText(), onPress, tooltip);
	}

	Button(int x, int y, int width, int height, String message, IPressable onPress,
			Tooltip tooltip) {
		super(0, x, y, width, height, message);
		this.onPress = onPress;
		this.tooltip = tooltip;
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		super.onClick(mouseX, mouseY);
		if (onPress != null) onPress.onPress(this);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
		if (tooltip != null && isMouseOver()) tooltip.render(this, mouseX, mouseY);
	}

	void setMessage(String message) {
		displayString = message;
	}

	String getMessage() {
		return displayString;
	}

	interface IPressable {
		void onPress(Button button);
	}

	interface Tooltip {
		void render(Button button, int mouseX, int mouseY);
	}
}
