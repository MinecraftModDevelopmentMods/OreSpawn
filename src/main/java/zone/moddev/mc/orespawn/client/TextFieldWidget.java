package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** 1.15 string-widget bridge retaining the later editor method names internally. */
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
}
