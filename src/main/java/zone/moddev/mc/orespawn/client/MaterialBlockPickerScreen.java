package zone.moddev.mc.orespawn.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Registry-backed block picker for surface, fluid, snow, and ice materials. */
final class MaterialBlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final boolean fluidOnly;
	private final Consumer<String> select;
	private String searchText = "";
	private int page;
	private TextFieldWidget search;

	MaterialBlockPickerScreen(Screen parent, GeologyEditorSession session,
			boolean fluidOnly, Consumer<String> select) {
		super(new TranslationTextComponent("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
		this.fluidOnly = fluidOnly;
		this.select = select;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		search = addButton(new TextFieldWidget(font, left, 36, contentWidth - 75, 20,
				new TranslationTextComponent("option.orespawn.search")));
		search.setValue(searchText);
		addButton(new Button(left + contentWidth - 70, 36, 70, 20,
				new TranslationTextComponent("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));
		List<String> ids = session.availableMaterialBlockIds(searchText, fluidOnly);
		int listTop = 64;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addButton(OreSpawnScreenLayout.button(this, font, left,
					listTop + i * 24, contentWidth, 20, new StringTextComponent(id), button -> {
						select.accept(id);
						onClose();
					}));
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

	private void rebuildWidgets() { buttons.clear(); children.clear(); init(); }
	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
		super.render(mouseX, mouseY, partialTick);
	}
}
