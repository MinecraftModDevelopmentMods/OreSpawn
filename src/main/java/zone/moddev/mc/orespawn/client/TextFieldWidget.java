package zone.moddev.mc.orespawn.client;

import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;

/** GuiButton-compatible host for Forge 12's native {@link GuiTextField}. */
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
	public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
		field.setVisible(visible);
		field.setEnabled(enabled);
		if (visible) field.drawTextBox();
	}

	@Override
	public boolean mousePressed(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
		if (!visible || !enabled) return false;
		field.mouseClicked(mouseX, mouseY, 0);
		return mouseX >= xPosition && mouseX < xPosition + width
				&& mouseY >= yPosition && mouseY < yPosition + height;
	}

	boolean keyTyped(char character, int keyCode) {
		return visible && enabled && field.textboxKeyTyped(character, keyCode);
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
		field.setGuiResponder(new net.minecraft.client.gui.GuiPageButtonList.GuiResponder() {
			@Override public void setEntryValue(int id, boolean value) { }
			@Override public void setEntryValue(int id, float value) { }
			@Override public void setEntryValue(int id, String value) { responder.accept(value); }
		});
	}
}
