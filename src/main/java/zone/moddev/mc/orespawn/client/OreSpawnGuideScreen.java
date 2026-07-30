package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** A short, player-facing guide available while a world is being configured. */
final class OreSpawnGuideScreen extends OreSpawnScreen {
	private static final List<GuidePage> PAGES = Arrays.asList(
			page("welcome", 3),
			page("world", 3),
			page("rocks", 3),
			page("ores", 3),
			page("patterns", 3),
			page("fluids", 3),
			page("biomes", 3),
			page("materials", 3),
			page("mods", 3),
			page("server", 3));

	private final Screen parent;
	private int page;

	OreSpawnGuideScreen(Screen parent) {
		super(Component.translatable("screen.orespawn.guide"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int bottom = OreSpawnScreenLayout.footerY(height);
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(left, bottom, 80, 20,
				Component.translatable("button.orespawn.previous"), button -> changePage(-1)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 70, bottom, 140, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 230, bottom, 80, 20,
				Component.translatable("button.orespawn.next"), button -> changePage(1)));
		previous.active = page > 0;
		next.active = page + 1 < PAGES.size();
	}

	private void changePage(int amount) {
		page = Math.max(0, Math.min(PAGES.size() - 1, page + amount));
		clearWidgets();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		GuidePage current = PAGES.get(page);
		graphics.drawCenteredString(font, title, width / 2, 10, OreSpawnScreenLayout.TEXT_PRIMARY);
		graphics.drawCenteredString(font, current.title, width / 2, 28, OreSpawnScreenLayout.TEXT_HIGHLIGHT);
		graphics.drawCenteredString(font,
				Component.literal((page + 1) + " / " + PAGES.size()), width / 2, 42, OreSpawnScreenLayout.TEXT_MUTED);

		int textWidth = Math.min(330, width - 32);
		int x = (width - textWidth) / 2;
		int y = 58;
		for (Component paragraph : current.paragraphs) {
			for (FormattedCharSequence line : font.split(paragraph, textWidth)) {
				graphics.drawString(font, line, x, y, OreSpawnScreenLayout.TEXT_BODY);
				y += 10;
			}
			y += 6;
		}

	}

	private static GuidePage page(String id, int paragraphCount) {
		Component[] paragraphs = new Component[paragraphCount];
		for (int i = 0; i < paragraphCount; i++) {
			paragraphs[i] = Component.translatable("guide.orespawn." + id + "." + (i + 1));
		}
		return new GuidePage(Component.translatable("guide.orespawn." + id + ".title"), paragraphs);
	}

	private static final class GuidePage {
		final Component title;
		final List<Component> paragraphs;

		GuidePage(Component title, Component[] paragraphs) {
			this.title = title;
			this.paragraphs = Arrays.asList(paragraphs);
		}
	}
}
