package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

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
		super(new TranslationTextComponent("screen.orespawn.guide"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int bottom = OreSpawnScreenLayout.footerY(height);
		Button previous = addButton(new Button(left, bottom, 80, 20,
				new TranslationTextComponent("button.orespawn.previous"), button -> changePage(-1)));
		addButton(new Button(width / 2 - 70, bottom, 140, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
		Button next = addButton(new Button(left + 230, bottom, 80, 20,
				new TranslationTextComponent("button.orespawn.next"), button -> changePage(1)));
		previous.active = page > 0;
		next.active = page + 1 < PAGES.size();
	}

	private void changePage(int amount) {
		page = Math.max(0, Math.min(PAGES.size() - 1, page + amount));
		buttons.clear(); children.clear();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		GuidePage current = PAGES.get(page);
		drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);
		drawCenteredString(poseStack, font, current.title, width / 2, 28, 0xFFFF55);
		drawCenteredString(poseStack, font,
				new StringTextComponent((page + 1) + " / " + PAGES.size()), width / 2, 42, 0xAAAAAA);

		int textWidth = Math.min(330, width - 32);
		int x = (width - textWidth) / 2;
		int y = 58;
		for (ITextComponent paragraph : current.paragraphs) {
			for (String line : font.listFormattedStringToWidth(paragraph.getFormattedText(), textWidth)) {
				font.drawString(line, x, y, 0xEEEEEE);
				y += 10;
			}
			y += 6;
		}
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private static GuidePage page(String id, int paragraphCount) {
		ITextComponent[] paragraphs = new ITextComponent[paragraphCount];
		for (int i = 0; i < paragraphCount; i++) {
			paragraphs[i] = new TranslationTextComponent("guide.orespawn." + id + "." + (i + 1));
		}
		return new GuidePage(new TranslationTextComponent("guide.orespawn." + id + ".title"), paragraphs);
	}

	private static final class GuidePage {
		final ITextComponent title;
		final List<ITextComponent> paragraphs;

		GuidePage(ITextComponent title, ITextComponent[] paragraphs) {
			this.title = title;
			this.paragraphs = Arrays.asList(paragraphs);
		}
	}
}
