package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Wraps Minecraft 1.14's text field with the editor's string-based constructor. */
class TextFieldWidget extends net.minecraft.client.gui.widget.TextFieldWidget {
	TextFieldWidget(FontRenderer font, int x, int y, int width, int height, ITextComponent label) {
		this(font, x, y, width, height, label.getFormattedText());
	}

	TextFieldWidget(FontRenderer font, int x, int y, int width, int height, String label) {
		super(font, x, y, width, height, label);
	}

	void setValue(String value) {
		setText(value);
	}

	String getValue() {
		return getText();
	}

	void setMaxLength(int length) {
		setMaxStringLength(length);
	}

	void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
}
