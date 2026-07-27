package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Shared dimensions for the compact world-creation screens. */
final class OreSpawnScreenLayout {
	private static final int COMPACT_HEIGHT = 260;
	private static final Map<Screen, List<ExplainedWidget>> EXPLANATIONS = new WeakHashMap<>();

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
		return new TextComponent(font.plainSubstrByWidth(message.getString(), available) + suffix);
	}

	static Button button(Screen screen, Font font, int x, int y, int width, int height,
			Component message, Button.OnPress onPress) {
		Component fitted = fit(font, message, Math.max(0, width - 8));
		if (fitted == message) {
			return new Button(x, y, width, height, message, onPress);
		}
		return new Button(x, y, width, height, fitted, onPress,
				(button, poseStack, mouseX, mouseY) -> screen.renderTooltip(poseStack,
						font.split(message, Math.max(180, Math.min(310, screen.width - 20))), mouseX, mouseY));
	}

	static Button explainedButton(Screen screen, Font font, int x, int y, int width, int height,
			Component message, Button.OnPress onPress, String translationKey) {
		return explain(screen, button(screen, font, x, y, width, height, message, onPress), translationKey);
	}

	static void beginHelp(Screen screen) {
		EXPLANATIONS.remove(screen);
	}

	static <T extends AbstractWidget> T explain(Screen screen, T widget, String translationKey) {
		EXPLANATIONS.computeIfAbsent(screen, ignored -> new ArrayList<>())
				.add(new ExplainedWidget(widget, translationKey));
		return widget;
	}

	static void renderExplanations(Screen screen, PoseStack poseStack, int mouseX, int mouseY) {
		List<ExplainedWidget> explanations = EXPLANATIONS.get(screen);
		if (explanations == null) {
			return;
		}
		for (ExplainedWidget explanation : explanations) {
			if (explanation.widget.visible && explanation.widget.isMouseOver(mouseX, mouseY)) {
				Font font = Minecraft.getInstance().font;
				screen.renderTooltip(poseStack,
						font.split(new TranslatableComponent(explanation.translationKey),
								Math.max(180, Math.min(310, screen.width - 20))),
						mouseX, mouseY);
				return;
			}
		}
	}

	private static final class ExplainedWidget {
		final AbstractWidget widget;
		final String translationKey;

		ExplainedWidget(AbstractWidget widget, String translationKey) {
			this.widget = widget;
			this.translationKey = translationKey;
		}
	}
}
