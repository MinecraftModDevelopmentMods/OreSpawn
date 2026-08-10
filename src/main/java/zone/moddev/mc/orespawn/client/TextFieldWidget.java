package zone.moddev.mc.orespawn.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;

/** GuiButton-compatible host for Forge 25's native {@link GuiTextField}. */
class TextFieldWidget extends net.minecraft.client.gui.GuiButton {
	private final GuiTextField field;

	TextFieldWidget(FontRenderer font, int x, int y, int width, int height, ITextComponent label) {
		this(font, x, y, width, height, label.getFormattedText());
	}

	TextFieldWidget(FontRenderer font, int x, int y, int width, int height, String label) {
		super(0, x, y, width, height, label);
		field = new GuiTextField(0, font, x, y, width, height);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTicks) {
		field.setVisible(visible);
		field.setEnabled(enabled);
		if (visible) field.drawTextField(mouseX, mouseY, partialTicks);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return visible && enabled && field.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return visible && enabled && field.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean charTyped(char character, int modifiers) {
		return visible && enabled && field.charTyped(character, modifiers);
	}

	@Override
	public void focusChanged(boolean focused) {
		field.setFocused(focused);
	}

	void setValue(String value) {
		field.setText(value);
	}

	String getValue() {
		return field.getText();
	}

	void setMaxLength(int length) {
		field.setMaxStringLength(length);
	}

	void func_212954_a(Consumer<String> responder) {
		field.setTextAcceptHandler((id, value) -> responder.accept(value));
	}
}
