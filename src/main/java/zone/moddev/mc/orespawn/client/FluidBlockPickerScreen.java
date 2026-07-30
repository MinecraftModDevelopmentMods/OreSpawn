package zone.moddev.mc.orespawn.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

final class FluidBlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private EditBox search;
	private String searchText = "";
	private int page;

	FluidBlockPickerScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(260, width - 24));
		int left = (width - contentWidth) / 2;
		int searchButtonWidth = 70;
		search = addRenderableWidget(new EditBox(font, left, 40,
				contentWidth - searchButtonWidth - 5, 20,
				Component.translatable("option.orespawn.search")));
		search.setValue(searchText);
		addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left + contentWidth - searchButtonWidth, 40, searchButtonWidth, 20,
				Component.translatable("button.orespawn.search"), button -> {
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));

		List<String> ids = session.availableFluidBlockIds(searchText);
		int listTop = 68;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, listTop + (i * 24), contentWidth, 20,
					OreSpawnScreenLayout.fit(font, fluidName(id), contentWidth - 8),
					button -> choose(id),
					Component.literal(id)));
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

	private void choose(String blockId) {
		String ruleId = session.assignFluidDeposit(blockId);
		if (ruleId != null) {
			minecraft.setScreen(new FluidDepositEntryScreen(parent, session, ruleId));
		}
	}

	private Component fluidName(String id) {
		try {
			Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
			return block == null ? Component.literal(id) : Component.translatable(block.getDescriptionId());
		} catch (RuntimeException ignored) {
			return Component.literal(id);
		}
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
		graphics.drawCenteredString(font, title, width / 2, 18, OreSpawnScreenLayout.TEXT_PRIMARY);
	}
}
