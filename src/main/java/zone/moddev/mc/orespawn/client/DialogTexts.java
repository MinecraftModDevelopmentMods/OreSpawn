package zone.moddev.mc.orespawn.client;

import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Shared translated labels for Minecraft 1.10 screens. */
final class DialogTexts {
	static final TextComponentTranslation GUI_DONE = new TextComponentTranslation("gui.done");
	static final TextComponentTranslation GUI_CANCEL = new TextComponentTranslation("gui.cancel");

	/** Minecraft 1.10 stores translated block names under {@code tile.*.name}. */
	static TextComponentTranslation blockName(Block block) {
		return new TextComponentTranslation(block.getUnlocalizedName() + ".name");
	}

	static ITextComponent blockName(Block block, String fallback) {
		if (block == null) return new TextComponentString(fallback);
		return blockName(block, fallback, I18n::hasKey);
	}

	static ITextComponent blockName(Block block, String fallback,
			Predicate<String> translationExists) {
		if (block == null) return new TextComponentString(fallback);
		String key = block.getUnlocalizedName() + ".name";
		if (!"tile.null.name".equals(key) && translationExists.test(key)) {
			return new TextComponentTranslation(key);
		}
		Fluid fluid = FluidRegistry.lookupFluidForBlock(block);
		String fluidKey = fluid == null ? null : fluid.getUnlocalizedName();
		if (fluidKey != null && translationExists.test(fluidKey)) {
			return new TextComponentTranslation(fluidKey);
		}
		return new TextComponentString(friendlyRegistryName(fallback));
	}

	private static String friendlyRegistryName(String registryId) {
		if (registryId == null || registryId.trim().isEmpty()) return "Unknown";
		String path = registryId.trim();
		int colon = path.indexOf(':');
		if (colon >= 0 && colon + 1 < path.length()) path = path.substring(colon + 1);
		int slash = path.lastIndexOf('/');
		if (slash >= 0 && slash + 1 < path.length()) path = path.substring(slash + 1);
		path = path.replace('_', ' ').replace('-', ' ').replace('.', ' ').trim();
		if (path.isEmpty()) return registryId;

		StringBuilder friendly = new StringBuilder(path.length());
		boolean capitalize = true;
		for (int i = 0; i < path.length(); i++) {
			char current = path.charAt(i);
			if (Character.isWhitespace(current)) {
				if (friendly.length() > 0 && friendly.charAt(friendly.length() - 1) != ' ') {
					friendly.append(' ');
				}
				capitalize = true;
			} else {
				friendly.append(capitalize ? Character.toUpperCase(current)
						: Character.toLowerCase(current));
				capitalize = false;
			}
		}
		return friendly.toString();
	}

	private DialogTexts() {
	}
}
