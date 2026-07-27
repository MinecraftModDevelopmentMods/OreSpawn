package zone.moddev.mc.orespawn.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class BiomePaletteScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private int page;

	BiomePaletteScreen(Screen parent, GeologyEditorSession session, String dimension) {
		super(Component.translatable("screen.orespawn.biome_palette"));
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
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, y,
					contentWidth - removeWidth - 5, 20, Component.literal(id),
					button -> minecraft.setScreen(new BiomePlacementScreen(this, session,
							dimension, id))));
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left + contentWidth - removeWidth, y, removeWidth, 20,
					Component.translatable("button.orespawn.remove"),
					button -> { session.removeBiomePlacement(dimension, id); rebuildWidgets(); }));
		}
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(left, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 50, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left + contentWidth - 150, controlsY, 150, 20,
				Component.translatable("button.orespawn.add_biome"),
				button -> minecraft.setScreen(new BiomePickerScreen(this, session, id -> {
					session.addBiomePlacement(dimension, id);
					minecraft.setScreen(new BiomePlacementScreen(this, session, dimension, id));
				}))));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(poseStack, font, Component.literal(dimension), width / 2, 28, 0xCCCCCC);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
