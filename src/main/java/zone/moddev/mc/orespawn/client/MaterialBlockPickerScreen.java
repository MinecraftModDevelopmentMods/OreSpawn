package zone.moddev.mc.orespawn.client;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Registry-backed block picker for surface, fluid, snow, and ice materials. */
final class MaterialBlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final boolean fluidOnly;
	private final Consumer<String> select;
	private String searchText = "";
	private int page;
	private EditBox search;

	MaterialBlockPickerScreen(Screen parent, GeologyEditorSession session,
			boolean fluidOnly, Consumer<String> select) {
		super(Component.translatable("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
		this.fluidOnly = fluidOnly;
		this.select = select;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		search = addRenderableWidget(new EditBox(font, left, 36, contentWidth - 75, 20,
				Component.translatable("option.orespawn.search")));
		search.setValue(searchText);
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + contentWidth - 70, 36, 70, 20,
				Component.translatable("button.orespawn.search"), button -> {
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
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left,
					listTop + i * 24, contentWidth, 20, Component.literal(id), button -> {
						select.accept(id);
						onClose();
					}));
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

	protected void rebuildWidgets() { clearWidgets(); init(); }
	@Override public void onClose() { minecraft.gui.setScreen(parent); }

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		graphics.centeredText(font, title, width / 2, 14, OreSpawnScreenLayout.TEXT_PRIMARY);

	}
}
