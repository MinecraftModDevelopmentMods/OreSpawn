package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared dimensions for the compact world-creation screens. */
final class OreSpawnScreenLayout {
	static final int TEXT_PRIMARY = 0xFFFFFFFF;
	static final int TEXT_SECONDARY = 0xFFDDDDDD;
	static final int TEXT_SOFT = 0xFFCCCCCC;
	static final int TEXT_MUTED = 0xFFAAAAAA;
	static final int TEXT_BODY = 0xFFEEEEEE;
	static final int TEXT_HIGHLIGHT = 0xFFFFFF55;
	static final int TEXT_ERROR = 0xFFFF5555;

	private static final int COMPACT_HEIGHT = 260;

	private OreSpawnScreenLayout() { }

	static boolean compact(int height) {
		return height < COMPACT_HEIGHT;
	}

	static int mainTop(int height) {
		return compact(height) ? 28 : 42;
	}

	static int mainRowSpacing(int height) {
		return compact(height) ? 22 : 24;
	}

	static int worldCreationRowSpacing(int availableHeight, int rows) {
		return rows <= 1 ? 4 : Math.min(4, Math.max(0, (availableHeight - 2 - rows * 20) / (rows - 1)));
	}

	static int worldCreationContentWidth(int width) {
		return Math.min(385, Math.max(5, width - 20));
	}

	static int mainTitleY(int height) {
		return compact(height) ? 7 : 16;
	}

	static int mainErrorY(int height) {
		return compact(height) ? 18 : 30;
	}

	static int compactOrePlacementLabelY(int height, int row) {
		int available = footerY(height) - 4 - 134;
		int spacing = Math.min(36, Math.max(30, available / 2));
		return 104 + (row * spacing);
	}

	static int compactOrePlacementFieldY(int height, int row) {
		return compactOrePlacementLabelY(height, row) + 10;
	}

	static int footerY(int height) {
		return height - 28;
	}

	static Component fit(Font font, Component message, int width) {
		if (font.width(message) <= width) {
			return message;
		}
		String suffix = "...";
		int available = Math.max(0, width - font.width(suffix));
		return Component.literal(font.plainSubstrByWidth(message.getString(), available) + suffix);
	}

	static Button button(Screen screen, Font font, int x, int y, int width, int height,
			Component message, net.minecraft.client.gui.components.Button.OnPress onPress) {
		Component fitted = fit(font, message, Math.max(0, width - 8));
		Button button = new Button(x, y, width, height, fitted, onPress);
		if (fitted != message) button.setTooltip(Tooltip.create(message));
		return button;
	}

	static Button plainButton(int x, int y, int width, int height,
			Component message, Button.OnPress onPress) {
		return new Button(x, y, width, height, message, onPress);
	}

	static Button explainedButton(Screen screen, Font font, int x, int y, int width, int height,
			Component message, Button.OnPress onPress, String translationKey) {
		return explain(button(screen, font, x, y, width, height, message, onPress), translationKey);
	}

	static Button plainButton(int x, int y, int width, int height,
			Component message, Button.OnPress onPress, Component tooltip) {
		Button button = new Button(x, y, width, height, message, onPress);
		button.setTooltip(Tooltip.create(tooltip));
		return button;
	}

	static <T extends AbstractWidget> T explain(T widget, String translationKey) {
		widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
		return widget;
	}

	static <T extends AbstractWidget> T explain(Screen screen, T widget, String translationKey) {
		return explain(widget, translationKey);
	}

	static void renderExplanations(Screen screen, int mouseX, int mouseY) {
		// Minecraft 1.20.1 renders widget tooltips itself.
	}

	static void beginHelp(Screen screen) {
		// Tooltips are attached directly to widgets on this target.
	}

	static Component tooltip(java.util.List<Component> lines) {
		net.minecraft.network.chat.MutableComponent result = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) result.append("\n");
			result.append(lines.get(i));
		}
		return result;
	}
}
