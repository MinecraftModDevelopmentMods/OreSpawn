package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.MaterialTab;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class GeologyMaterialsScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private MaterialTab tab = MaterialTab.SEDIMENTARY;
	private EditBox search;
	private boolean showAll;
	private int page;
	private String searchText = "";

	GeologyMaterialsScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.materials"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
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
			Button button = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
					tabX, 26 + (tabRow * 24), tabWidth, 20,
					Component.translatable("tab.orespawn." + value.key), selected -> changeTab(value)));
			button.active = value != tab;
			OreSpawnScreenLayout.explain(button, "tooltip.orespawn.material.tab." + value.key);
		}

		int tabRows = (MaterialTab.values().length + tabsPerRow - 1) / tabsPerRow;
		int searchY = 28 + (tabRows * 24);
		int searchButtonWidth = 60;
		int advancedWidth = tab == MaterialTab.UNASSIGNED ? 85 : 0;
		int searchWidth = contentWidth - searchButtonWidth - advancedWidth - (advancedWidth > 0 ? 10 : 5);
		search = addRenderableWidget(new EditBox(font, contentLeft, searchY, searchWidth, 20,
				Component.translatable("option.orespawn.search")));
		search.setValue(searchText);
		int searchButtonX = contentLeft + searchWidth + 5;
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, searchButtonX, searchY, searchButtonWidth, 20,
				Component.translatable("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));
		Button advanced = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				searchButtonX + searchButtonWidth + 5, searchY, 85, 20,
				Component.translatable(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
				button -> { showAll = !showAll; page = 0; rebuildWidgets(); }));
		advanced.visible = tab == MaterialTab.UNASSIGNED;
		OreSpawnScreenLayout.explain(advanced,
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
			Component rowMessage = Component.literal(rowLabel(id));
			Component fittedRow = OreSpawnScreenLayout.fit(font, rowMessage, entryWidth - 8);
			List<Component> details = rowDetails(id);
			addRenderableWidget(OreSpawnScreenLayout.plainButton(contentLeft, y, entryWidth, 20,
					fittedRow, button -> openEntry(id),
					OreSpawnScreenLayout.tooltip(details)));
			if (tab != MaterialTab.UNASSIGNED) {
				addRenderableWidget(OreSpawnScreenLayout.button(this, font,
						contentLeft + entryWidth + rowGap, y, removeWidth, 20,
						Component.translatable("button.orespawn.remove"), button -> removeEntry(id)));
			}
		}

		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(contentLeft, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(contentLeft + 50, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;

		int addWidth = Math.min(150, contentWidth - 105);
		Button add = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				contentLeft + contentWidth - addWidth, controlsY, addWidth, 20,
				Component.translatable("button.orespawn.add_block"), button -> openBlockPicker()));
		add.visible = tab != MaterialTab.UNASSIGNED;
		OreSpawnScreenLayout.explain(add, "tooltip.orespawn.material.add_block");
		int doneWidth = Math.min(150, contentWidth);
		addRenderableWidget(OreSpawnScreenLayout.plainButton((width - doneWidth) / 2, height - 28, doneWidth, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private void changeTab(MaterialTab value) {
		tab = value;
		page = 0;
		rebuildWidgets();
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private List<String> currentIds() {
		return session.materialIds(tab, searchText, showAll);
	}

	private String rowLabel(String id) {
		return session.materialBlockId(tab, id);
	}

	private List<Component> rowDetails(String id) {
		List<Component> details = new ArrayList<>();
		String block = session.materialBlockId(tab, id);
		details.add(Component.literal(block));
		if (!block.equals(id)) {
			details.add(Component.translatable("option.orespawn.registry_id").append(": ")
					.append(Component.literal(id)));
		}
		if (tab == MaterialTab.ORES) {
			String source = GeologyEditorSession.string(session.ore(id), "source_provider",
					GeologyEditorSession.string(session.ore(id), "source_mod", ""));
			if (!source.isEmpty()) {
				details.add(Component.literal(source));
			}
		}
		return details;
	}

	private void openEntry(String id) {
		if (tab == MaterialTab.ORES) {
			minecraft.gui.setScreen(new OreEntryScreen(this, session, id));
		} else if (tab == MaterialTab.UNASSIGNED) {
			minecraft.gui.setScreen(new BlockAssignmentScreen(this, session, id));
		} else {
			minecraft.gui.setScreen(new RockEntryScreen(this, session, id));
		}
	}

	private void openBlockPicker() {
		minecraft.gui.setScreen(new BlockPickerScreen(this, session, tab));
	}

	private void removeEntry(String id) {
		if (tab == MaterialTab.ORES) session.disableOrRemoveOre(id);
		else session.removeRock(id);
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		graphics.centeredText(font, title, width / 2, 10, OreSpawnScreenLayout.TEXT_PRIMARY);

	}
}
