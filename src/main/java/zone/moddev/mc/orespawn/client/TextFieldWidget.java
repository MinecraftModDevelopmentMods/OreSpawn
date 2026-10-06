package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

/** Keeps editor field construction consistent with the native 1.16 widget. */
class TextFieldWidget extends net.minecraft.client.gui.widget.TextFieldWidget {
	TextFieldWidget(FontRenderer font, int x, int y, int width, int height, String label) {
		this(font, x, y, width, height, new StringTextComponent(label));
	}

	TextFieldWidget(FontRenderer font, int x, int y, int width, int height,
			ITextComponent label) {
		super(font, x, y, width, height, label);
	}

	void setBounds(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
}
