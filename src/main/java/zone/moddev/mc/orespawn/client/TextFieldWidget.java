package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Keeps editor field construction consistent with the native 1.16 widget. */
class TextFieldWidget extends net.minecraft.client.gui.components.EditBox {
	TextFieldWidget(Font font, int x, int y, int width, int height, String label) {
		this(font, x, y, width, height, Component.literal(label));
	}

	TextFieldWidget(Font font, int x, int y, int width, int height,
			Component label) {
		super(font, x, y, width, height, label);
	}

	void setBounds(int x, int y, int width, int height) {
		setX(x);
		setY(y);
		this.width = width;
		this.height = height;
	}
}
