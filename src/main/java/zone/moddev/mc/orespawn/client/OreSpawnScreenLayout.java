package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Shared dimensions for the compact world-creation screens. */
final class OreSpawnScreenLayout {
	private static final int COMPACT_HEIGHT = 260;
	private static final Map<OreSpawnScreen, List<ExplainedWidget>> EXPLANATIONS = new WeakHashMap<>();

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

	static String fit(FontRenderer font, ITextComponent message, int width) {
		String text = message.getFormattedText();
		if (font.getStringWidth(text) <= width) {
			return text;
		}
		String suffix = "...";
		int available = Math.max(0, width - font.getStringWidth(suffix));
		return font.trimStringToWidth(text, available) + suffix;
	}

	static Button button(OreSpawnScreen screen, FontRenderer font, int x, int y, int width, int height,
			ITextComponent message, Button.IPressable onPress) {
		String fitted = fit(font, message, Math.max(0, width - 8));
		Button button = new Button(x, y, width, height, fitted, onPress);
		if (!fitted.equals(message.getFormattedText())) {
			explainText(screen, button, message.getFormattedText());
		}
		return button;
	}

	static Button explainedButton(OreSpawnScreen screen, FontRenderer font, int x, int y, int width, int height,
			ITextComponent message, Button.IPressable onPress, String translationKey) {
		return explain(screen, button(screen, font, x, y, width, height, message, onPress), translationKey);
	}

	static void beginHelp(OreSpawnScreen screen) {
		EXPLANATIONS.remove(screen);
	}

	static <T extends GuiButton> T explain(OreSpawnScreen screen, T widget, String translationKey) {
		EXPLANATIONS.computeIfAbsent(screen, ignored -> new ArrayList<>())
				.add(new ExplainedWidget(widget, translationKey, true));
		return widget;
	}

	private static void explainText(OreSpawnScreen screen, GuiButton widget, String text) {
		EXPLANATIONS.computeIfAbsent(screen, ignored -> new ArrayList<>())
				.add(new ExplainedWidget(widget, text, false));
	}

	static void renderExplanations(OreSpawnScreen screen, int mouseX, int mouseY) {
		List<ExplainedWidget> explanations = EXPLANATIONS.get(screen);
		if (explanations == null) {
			return;
		}
		for (ExplainedWidget explanation : explanations) {
			if (explanation.widget.visible && mouseX >= explanation.widget.x
					&& mouseX < explanation.widget.x + explanation.widget.width
					&& mouseY >= explanation.widget.y
					&& mouseY < explanation.widget.y + explanation.widget.height) {
				FontRenderer font = Minecraft.getMinecraft().fontRenderer;
				String text = explanation.translation
						? new TextComponentTranslation(explanation.text).getFormattedText()
						: explanation.text;
				screen.renderStringTooltip(font.listFormattedStringToWidth(text,
						Math.max(180, Math.min(310, screen.width - 20))), mouseX, mouseY);
				return;
			}
		}
	}

	private static final class ExplainedWidget {
		final GuiButton widget;
		final String text;
		final boolean translation;

		ExplainedWidget(GuiButton widget, String text, boolean translation) {
			this.widget = widget;
			this.text = text;
			this.translation = translation;
		}
	}
}
