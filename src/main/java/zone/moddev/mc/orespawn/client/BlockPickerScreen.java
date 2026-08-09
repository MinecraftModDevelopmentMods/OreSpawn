package zone.moddev.mc.orespawn.client;

import java.util.List;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.MaterialTab;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Registry-backed picker; text narrows installed blocks but never creates an ID. */
final class BlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final MaterialTab target;
	private String searchText = "";
	private String namespace = "";
	private boolean showAll;
	private int page;
	private TextFieldWidget search;

	BlockPickerScreen(Screen parent, GeologyEditorSession session, MaterialTab target) {
		super(new TranslationTextComponent("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
		this.target = target;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int left = width / 2 - 155;
		search = addButton(new TextFieldWidget(font, left, 34, 230, 20,
				new TranslationTextComponent("option.orespawn.search")));
		search.setMaxLength(128);
		search.setValue(searchText);
		addButton(new Button(left + 235, 34, 75, 20,
				new TranslationTextComponent("button.orespawn.search"), button -> {
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));

		List<String> namespaces = session.installedBlockNamespaces();
		if (!namespaces.contains(namespace)) namespace = "";
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::namespaceName)
				.withValues(namespaces).withInitialValue(namespace)
				.create(left, 58, 150, 20, new TranslationTextComponent("option.orespawn.mod_filter"),
						(button, value) -> { namespace = value; page = 0; rebuildWidgets(); })),
				"tooltip.orespawn.picker.mod_filter");
		OreSpawnScreenLayout.explain(this, addButton(new Button(left + 160, 58, 150, 20,
				new TranslationTextComponent(showAll ? "button.orespawn.safe_only" : "button.orespawn.show_all"),
				button -> { showAll = !showAll; page = 0; rebuildWidgets(); })),
				showAll ? "tooltip.orespawn.material.safe_only" : "tooltip.orespawn.material.show_all");

		List<String> ids = session.availableBlockIds(searchText, namespace, showAll);
		int listTop = 84;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addButton(new Button(left, listTop + (i * 24), 310, 20,
					new StringTextComponent(id), button -> select(id)));
		}
		Button previous = addButton(new Button(left, controlsY, 45, 20,
				new StringTextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(left + 50, controlsY, 45, 20,
				new StringTextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_CANCEL, button -> onClose()));
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

	private ITextComponent namespaceName(String value) {
		return value.isEmpty() ? new TranslationTextComponent("value.orespawn.all_mods") : new StringTextComponent(value);
	}

	private void rebuildWidgets() {
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
		drawCenteredString(poseStack, font, title, width / 2, 8, 0xFFFFFF);
		drawCenteredString(poseStack, font,
				new TranslationTextComponent("label.orespawn.adding_to",
						new TranslationTextComponent("tab.orespawn." + target.key)),
				width / 2, 20, 0xCCCCCC);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
