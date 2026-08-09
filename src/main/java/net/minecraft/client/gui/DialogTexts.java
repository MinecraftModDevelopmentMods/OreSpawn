package net.minecraft.client.gui;

import net.minecraft.util.text.TranslationTextComponent;

/** Forge 1.15 compatibility constants matching the later vanilla helper. */
public final class DialogTexts {
	public static final TranslationTextComponent GUI_DONE = text("gui.done");
	public static final TranslationTextComponent GUI_CANCEL = text("gui.cancel");

	private DialogTexts() {
	}

	private static TranslationTextComponent text(String key) {
		return new TranslationTextComponent(key);
	}
}
