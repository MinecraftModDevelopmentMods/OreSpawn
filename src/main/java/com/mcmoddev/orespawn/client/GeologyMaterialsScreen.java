package com.mcmoddev.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.mcmoddev.orespawn.client.GeologyEditorSession.MaterialTab;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

final class GeologyMaterialsScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private MaterialTab tab = MaterialTab.SEDIMENTARY;
	private EditBox search;
	private boolean showAll;
	private int page;
	private String searchText = "";

	GeologyMaterialsScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslatableComponent("screen.orespawn.materials"));
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
					new TranslatableComponent("tab.orespawn." + value.key), selected -> changeTab(value)));
			button.active = value != tab;
		}

		int tabRows = (MaterialTab.values().length + tabsPerRow - 1) / tabsPerRow;
		int searchY = 28 + (tabRows * 24);
		int searchButtonWidth = 60;
		int advancedWidth = tab == MaterialTab.UNASSIGNED ? 85 : 0;
		int searchWidth = contentWidth - searchButtonWidth - advancedWidth - (advancedWidth > 0 ? 10 : 5);
		search = addRenderableWidget(new EditBox(font, contentLeft, searchY, searchWidth, 20,
				new TranslatableComponent("option.orespawn.search")));
		search.setValue(searchText);
		int searchButtonX = contentLeft + searchWidth + 5;
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, searchButtonX, searchY, searchButtonWidth, 20,
				new TranslatableComponent("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));
		Button advanced = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				searchButtonX + searchButtonWidth + 5, searchY, 85, 20,
				new TranslatableComponent(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
				button -> { showAll = !showAll; page = 0; rebuildWidgets(); }));
		advanced.visible = tab == MaterialTab.UNASSIGNED;

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
			Component rowMessage = new TextComponent(rowLabel(id));
			Component fittedRow = OreSpawnScreenLayout.fit(font, rowMessage, entryWidth - 8);
			List<Component> details = rowDetails(id);
			addRenderableWidget(new Button(contentLeft, y, entryWidth, 20,
					fittedRow, button -> openEntry(id),
					(button, poseStack, mouseX, mouseY) -> renderComponentTooltip(
							poseStack, details, mouseX, mouseY)));
			if (tab != MaterialTab.UNASSIGNED) {
				addRenderableWidget(OreSpawnScreenLayout.button(this, font,
						contentLeft + entryWidth + rowGap, y, removeWidth, 20,
						new TranslatableComponent("button.orespawn.remove"), button -> removeEntry(id)));
			}
		}

		Button previous = addRenderableWidget(new Button(contentLeft, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(contentLeft + 50, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;

		int addWidth = Math.min(150, contentWidth - 105);
		Button add = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				contentLeft + contentWidth - addWidth, controlsY, addWidth, 20,
				new TranslatableComponent("button.orespawn.add_block"), button -> openBlockPicker()));
		add.visible = tab != MaterialTab.UNASSIGNED;
		int doneWidth = Math.min(150, contentWidth);
		addRenderableWidget(new Button((width - doneWidth) / 2, height - 28, doneWidth, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private void changeTab(MaterialTab value) {
		tab = value;
		page = 0;
		rebuildWidgets();
	}

	private void rebuildWidgets() {
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
		details.add(new TextComponent(block));
		if (!block.equals(id)) {
			details.add(new TranslatableComponent("option.orespawn.registry_id").append(": ")
					.append(new TextComponent(id)));
		}
		if (tab == MaterialTab.ORES) {
			String source = GeologyEditorSession.string(session.ore(id), "source_provider",
					GeologyEditorSession.string(session.ore(id), "source_mod", ""));
			if (!source.isEmpty()) {
				details.add(new TextComponent(source));
			}
		}
		return details;
	}

	private void openEntry(String id) {
		if (tab == MaterialTab.ORES) {
			minecraft.setScreen(new OreEntryScreen(this, session, id));
		} else if (tab == MaterialTab.UNASSIGNED) {
			minecraft.setScreen(new BlockAssignmentScreen(this, session, id));
		} else {
			minecraft.setScreen(new RockEntryScreen(this, session, id));
		}
	}

	private void openBlockPicker() {
		minecraft.setScreen(new BlockPickerScreen(this, session, tab));
	}

	private void removeEntry(String id) {
		if (tab == MaterialTab.ORES) session.disableOrRemoveOre(id);
		else session.removeRock(id);
		rebuildWidgets();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
