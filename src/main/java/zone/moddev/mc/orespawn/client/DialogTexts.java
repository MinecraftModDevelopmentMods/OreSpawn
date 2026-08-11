package zone.moddev.mc.orespawn.client;

import net.minecraft.block.Block;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Shared translated labels for Minecraft 1.10 screens. */
final class DialogTexts {
	static final TextComponentTranslation GUI_DONE = new TextComponentTranslation("gui.done");
	static final TextComponentTranslation GUI_CANCEL = new TextComponentTranslation("gui.cancel");

	/** Minecraft 1.10 stores translated block names under {@code tile.*.name}. */
	static TextComponentTranslation blockName(Block block) {
		return new TextComponentTranslation(block.getUnlocalizedName() + ".name");
	}

	static ITextComponent blockName(Block block, String fallback) {
		return block == null ? new TextComponentString(fallback) : blockName(block);
	}

	private DialogTexts() {
	}
}
