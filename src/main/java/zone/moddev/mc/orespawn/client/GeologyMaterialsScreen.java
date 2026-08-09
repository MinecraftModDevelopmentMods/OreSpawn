package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.MaterialTab;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

final class GeologyMaterialsScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private MaterialTab tab = MaterialTab.SEDIMENTARY;
	private TextFieldWidget search;
	private boolean showAll;
	private int page;
	private String searchText = "";

	GeologyMaterialsScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslationTextComponent("screen.orespawn.materials"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int contentWidth = Math.min(390, Math.max(240, width - 24));
		int contentLeft = (width - contentWidth) / 2;
		int tabGap = 4;
		int tabsPerRow = width < 390 ? 3 : MaterialTab.values().length;
		int tabWidth = (contentWidth - (tabGap * (tabsPerRow - 1))) / tabsPerRow;
		for (int i = 0; i < MaterialTab.values().length; i++) {
			MaterialTab value = MaterialTab.values()[i];
			int tabRow = i / tabsPerRow;
			int tabsInRow = Math.min(tabsPerRow, MaterialTab.values().length - (tabRow * tabsPerRow));
			int rowWidth = (tabsInRow * tabWidth) + ((tabsInRow - 1) * tabGap);
			int tabX = (width - rowWidth) / 2 + ((i % tabsPerRow) * (tabWidth + tabGap));
			Button button = addButton(OreSpawnScreenLayout.button(this, font,
					tabX, 26 + (tabRow * 24), tabWidth, 20,
					new TranslationTextComponent("tab.orespawn." + value.key), selected -> changeTab(value)));
			button.active = value != tab;
			OreSpawnScreenLayout.explain(this, button, "tooltip.orespawn.material.tab." + value.key);
		}

		int tabRows = (MaterialTab.values().length + tabsPerRow - 1) / tabsPerRow;
		int searchY = 28 + (tabRows * 24);
		int searchButtonWidth = 60;
		int advancedWidth = tab == MaterialTab.UNASSIGNED ? 85 : 0;
		int searchWidth = contentWidth - searchButtonWidth - advancedWidth - (advancedWidth > 0 ? 10 : 5);
		search = addButton(new TextFieldWidget(font, contentLeft, searchY, searchWidth, 20,
				new TranslationTextComponent("option.orespawn.search")));
		search.setValue(searchText);
		int searchButtonX = contentLeft + searchWidth + 5;
		addButton(OreSpawnScreenLayout.button(this, font, searchButtonX, searchY, searchButtonWidth, 20,
				new TranslationTextComponent("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));
		Button advanced = addButton(OreSpawnScreenLayout.button(this, font,
				searchButtonX + searchButtonWidth + 5, searchY, 85, 20,
				new TranslationTextComponent(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
				button -> { showAll = !showAll; page = 0; rebuildWidgets(); }));
		advanced.visible = tab == MaterialTab.UNASSIGNED;
		OreSpawnScreenLayout.explain(this, advanced,
				showAll ? "tooltip.orespawn.material.safe_only" : "tooltip.orespawn.material.show_all");

		List<String> ids = currentIds();
		int listTop = searchY + 28;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		int removeWidth = 78;
		int rowGap = 4;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			int y = listTop + (i * 24);
			int entryWidth = tab == MaterialTab.UNASSIGNED
					? contentWidth : contentWidth - removeWidth - rowGap;
			ITextComponent rowMessage = new StringTextComponent(rowLabel(id));
			String fittedRow = OreSpawnScreenLayout.fit(font, rowMessage, entryWidth - 8);
			List<ITextComponent> details = rowDetails(id);
			addButton(new Button(contentLeft, y, entryWidth, 20,
					fittedRow, button -> openEntry(id),
					(button, mouseX, mouseY) -> renderComponentTooltip(details, mouseX, mouseY)));
			if (tab != MaterialTab.UNASSIGNED) {
				addButton(OreSpawnScreenLayout.button(this, font,
						contentLeft + entryWidth + rowGap, y, removeWidth, 20,
						new TranslationTextComponent("button.orespawn.remove"), button -> removeEntry(id)));
			}
		}

		Button previous = addButton(new Button(contentLeft, controlsY, 45, 20,
				new StringTextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(contentLeft + 50, controlsY, 45, 20,
				new StringTextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;

		int addWidth = Math.min(150, contentWidth - 105);
		Button add = addButton(OreSpawnScreenLayout.button(this, font,
				contentLeft + contentWidth - addWidth, controlsY, addWidth, 20,
				new TranslationTextComponent("button.orespawn.add_block"), button -> openBlockPicker()));
		add.visible = tab != MaterialTab.UNASSIGNED;
		OreSpawnScreenLayout.explain(this, add, "tooltip.orespawn.material.add_block");
		int doneWidth = Math.min(150, contentWidth);
		addButton(new Button((width - doneWidth) / 2, height - 28, doneWidth, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void changeTab(MaterialTab value) {
		tab = value;
		page = 0;
		rebuildWidgets();
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	private List<String> currentIds() {
		return session.materialIds(tab, searchText, showAll);
	}

	private String rowLabel(String id) {
		return session.materialBlockId(tab, id);
	}

	private List<ITextComponent> rowDetails(String id) {
		List<ITextComponent> details = new ArrayList<>();
		String block = session.materialBlockId(tab, id);
		details.add(new StringTextComponent(block));
		if (!block.equals(id)) {
			details.add(new TranslationTextComponent("option.orespawn.registry_id").appendText(": ")
					.appendSibling(new StringTextComponent(id)));
		}
		if (tab == MaterialTab.ORES) {
			String source = GeologyEditorSession.string(session.ore(id), "source_provider",
					GeologyEditorSession.string(session.ore(id), "source_mod", ""));
			if (!source.isEmpty()) {
				details.add(new StringTextComponent(source));
			}
		}
		return details;
	}

	private void openEntry(String id) {
		if (tab == MaterialTab.ORES) {
			minecraft.displayGuiScreen(new OreEntryScreen(this, session, id));
		} else if (tab == MaterialTab.UNASSIGNED) {
			minecraft.displayGuiScreen(new BlockAssignmentScreen(this, session, id));
		} else {
			minecraft.displayGuiScreen(new RockEntryScreen(this, session, id));
		}
	}

	private void openBlockPicker() {
		minecraft.displayGuiScreen(new BlockPickerScreen(this, session, tab));
	}

	private void removeEntry(String id) {
		if (tab == MaterialTab.ORES) session.disableOrRemoveOre(id);
		else session.removeRock(id);
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
