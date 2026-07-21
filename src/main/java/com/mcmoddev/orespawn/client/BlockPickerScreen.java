package com.mcmoddev.orespawn.client;

import java.util.List;

import com.mcmoddev.orespawn.client.GeologyEditorSession.MaterialTab;
import com.mcmoddev.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Registry-backed picker; text narrows installed blocks but never creates an ID. */
final class BlockPickerScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final MaterialTab target;
	private String searchText = "";
	private String namespace = "";
	private boolean showAll;
	private int page;
	private EditBox search;

	BlockPickerScreen(Screen parent, GeologyEditorSession session, MaterialTab target) {
		super(new TranslatableComponent("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
		this.target = target;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		search = addRenderableWidget(new EditBox(font, left, 34, 230, 20,
				new TranslatableComponent("option.orespawn.search")));
		search.setMaxLength(128);
		search.setValue(searchText);
		addRenderableWidget(new Button(left + 235, 34, 75, 20,
				new TranslatableComponent("button.orespawn.search"), button -> {
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));

		List<String> namespaces = session.installedBlockNamespaces();
		if (!namespaces.contains(namespace)) namespace = "";
		addRenderableWidget(CycleButton.builder(this::namespaceName)
				.withValues(namespaces).withInitialValue(namespace)
				.create(left, 58, 150, 20, new TranslatableComponent("option.orespawn.mod_filter"),
						(button, value) -> { namespace = value; page = 0; rebuildWidgets(); }));
		addRenderableWidget(new Button(left + 160, 58, 150, 20,
				new TranslatableComponent(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
				button -> { showAll = !showAll; page = 0; rebuildWidgets(); }));

		List<String> ids = session.availableBlockIds(searchText, namespace, showAll);
		int listTop = 84;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(new Button(left, listTop + (i * 24), 310, 20,
					new TextComponent(id), button -> select(id)));
		}
		Button previous = addRenderableWidget(new Button(left, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(left + 50, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addRenderableWidget(new Button(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_CANCEL, button -> onClose()));
	}

	private void select(String id) {
		switch (target) {
		case SEDIMENTARY:
			session.assignRock(id, RockFamily.SEDIMENTARY);
			minecraft.setScreen(new RockEntryScreen(parent, session, id));
			break;
		case METAMORPHIC:
			session.assignRock(id, RockFamily.METAMORPHIC);
			minecraft.setScreen(new RockEntryScreen(parent, session, id));
			break;
		case IGNEOUS:
			session.assignRock(id, RockFamily.IGNEOUS_INTRUSIVE);
			minecraft.setScreen(new RockEntryScreen(parent, session, id));
			break;
		case ORES:
			session.assignOre(id);
			minecraft.setScreen(new OreEntryScreen(parent, session, id));
			break;
		default:
			minecraft.setScreen(new BlockAssignmentScreen(parent, session, id));
		}
	}

	private Component namespaceName(String value) {
		return value.isEmpty() ? new TranslatableComponent("value.orespawn.all_mods") : new TextComponent(value);
	}

	private void rebuildWidgets() {
		clearWidgets();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 8, 0xFFFFFF);
		drawCenteredString(poseStack, font,
				new TranslatableComponent("label.orespawn.adding_to",
						new TranslatableComponent("tab.orespawn." + target.key)),
				width / 2, 20, 0xCCCCCC);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
