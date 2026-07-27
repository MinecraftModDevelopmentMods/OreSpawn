package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared dimensions for the compact world-creation screens. */
final class OreSpawnScreenLayout {
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
			Component message, Button.OnPress onPress) {
		Component fitted = fit(font, message, Math.max(0, width - 8));
		Button.Builder builder = Button.builder(fitted, onPress).bounds(x, y, width, height);
		if (fitted != message) {
			builder.tooltip(Tooltip.create(message));
		}
		return builder.build();
	}

	static Button plainButton(int x, int y, int width, int height,
			Component message, Button.OnPress onPress) {
		return Button.builder(message, onPress).bounds(x, y, width, height).build();
	}

	static Button plainButton(int x, int y, int width, int height,
			Component message, Button.OnPress onPress, Component tooltip) {
		return Button.builder(message, onPress).bounds(x, y, width, height)
				.tooltip(Tooltip.create(tooltip)).build();
	}

	static <T extends AbstractWidget> T explain(T widget, String translationKey) {
		widget.setTooltip(Tooltip.create(Component.translatable(translationKey)));
		return widget;
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
