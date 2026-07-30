package zone.moddev.mc.orespawn.client;

import java.util.List;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.MaterialTab;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Registry-backed picker; text narrows installed blocks but never creates an ID. */
final class BlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final MaterialTab target;
	private String searchText = "";
	private String namespace = "";
	private boolean showAll;
	private int page;
	private EditBox search;

	BlockPickerScreen(Screen parent, GeologyEditorSession session, MaterialTab target) {
		super(Component.translatable("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
		this.target = target;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		search = addRenderableWidget(new EditBox(font, left, 34, 230, 20,
				Component.translatable("option.orespawn.search")));
		search.setMaxLength(128);
		search.setValue(searchText);
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 235, 34, 75, 20,
				Component.translatable("button.orespawn.search"), button -> {
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));

		List<String> namespaces = session.installedBlockNamespaces();
		if (!namespaces.contains(namespace)) namespace = "";
		addRenderableWidget(CycleButton.builder(this::namespaceName)
				.withValues(namespaces).withInitialValue(namespace)
				.create(left, 58, 150, 20, Component.translatable("option.orespawn.mod_filter"),
						(button, value) -> { namespace = value; page = 0; rebuildWidgets(); }));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 160, 58, 150, 20,
				Component.translatable(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
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
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, listTop + (i * 24), 310, 20,
					Component.literal(id), button -> select(id)));
		}
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(left, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 50, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20,
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
		return value.isEmpty() ? Component.translatable("value.orespawn.all_mods") : Component.literal(value);
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		graphics.drawCenteredString(font, title, width / 2, 8, OreSpawnScreenLayout.TEXT_PRIMARY);
		graphics.drawCenteredString(font,
				Component.translatable("label.orespawn.adding_to",
						Component.translatable("tab.orespawn." + target.key)),
				width / 2, 20, OreSpawnScreenLayout.TEXT_SOFT);

	}
}
