package zone.moddev.mc.orespawn.client;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

final class BiomePaletteScreen extends OreSpawnScreen {
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private int page;

	BiomePaletteScreen(GuiScreen parent, GeologyEditorSession session, String dimension) {
		super(new TextComponentTranslation("screen.orespawn.biome_palette"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		int removeWidth = 70;
		int listTop = 48;
		int controlsY = height - 52;
		List<String> ids = session.biomePlacementIds(dimension);
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			int y = listTop + i * 24;
			addButton(OreSpawnScreenLayout.button(this, font, left, y,
					contentWidth - removeWidth - 5, 20, new TextComponentString(id),
					button -> minecraft.displayGuiScreen(new BiomePlacementScreen(this, session,
							dimension, id))));
			addButton(new Button(left + contentWidth - removeWidth, y, removeWidth, 20,
					new TextComponentTranslation("button.orespawn.remove"),
					button -> { session.removeBiomePlacement(dimension, id); rebuildWidgets(); }));
		}
		Button previous = addButton(new Button(left, controlsY, 45, 20,
				new TextComponentString("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(left + 50, controlsY, 45, 20,
				new TextComponentString(">"), button -> { page++; rebuildWidgets(); }));
		previous.enabled = page > 0;
		next.enabled = page + 1 < pageCount;
		addButton(OreSpawnScreenLayout.button(this, font,
				left + contentWidth - 150, controlsY, 150, 20,
				new TextComponentTranslation("button.orespawn.add_biome"),
				button -> minecraft.displayGuiScreen(new BiomePickerScreen(this, session, id -> {
					session.addBiomePlacement(dimension, id);
					minecraft.displayGuiScreen(new BiomePlacementScreen(this, session, dimension, id));
				}))));
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	@Override public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(font, new TextComponentString(dimension), width / 2, 28, 0xCCCCCC);
		super.render(mouseX, mouseY, partialTick);
	}
}
