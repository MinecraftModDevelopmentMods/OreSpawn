package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

final class FluidDepositListScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private int page;

	FluidDepositListScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.fluid_deposits"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(260, width - 24));
		int left = (width - contentWidth) / 2;
		List<String> ids = session.fluidDepositIds();
		addRenderableWidget(CycleButton.onOffBuilder(session.placeFluidDeposits())
				.withTooltip(value -> Tooltip.create(
						Component.translatable("tooltip.orespawn.fluid_deposits")))
				.create(left, 40, contentWidth, 20,
						Component.translatable("option.orespawn.fluid_deposits"),
						(button, value) -> session.setPlaceFluidDeposits(value)));
		int listTop = 68;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			int y = listTop + (i * 24);
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, y, contentWidth - 82, 20,
					OreSpawnScreenLayout.fit(font, depositName(id), contentWidth - 90),
					button -> minecraft.setScreen(new FluidDepositEntryScreen(this, session, id)),
					OreSpawnScreenLayout.tooltip(details(id))));
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left + contentWidth - 78, y, 78, 20,
					Component.translatable("button.orespawn.remove"), button -> {
						session.removeFluidDeposit(id);
						rebuildWidgets();
					}));
		}
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(left, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 50, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		int addWidth = Math.min(150, contentWidth - 105);
		addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left + contentWidth - addWidth, controlsY, addWidth, 20,
				Component.translatable("button.orespawn.add"), button ->
						minecraft.setScreen(new FluidBlockPickerScreen(this, session))));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private Component depositName(String id) {
		JsonObject deposit = session.fluidDeposit(id);
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(
					GeologyEditorSession.string(deposit, "block", "")));
			if (block != null) return Component.translatable(block.getDescriptionId());
		} catch (RuntimeException ignored) { }
		return Component.literal(id);
	}

	private List<Component> details(String id) {
		List<Component> result = new ArrayList<>();
		result.add(Component.literal(id));
		JsonObject deposit = session.fluidDeposit(id);
		result.add(Component.literal(GeologyEditorSession.string(deposit, "block", "")));
		String provider = GeologyEditorSession.string(deposit, "source_provider", "");
		if (!provider.isEmpty()) result.add(Component.literal(provider));
		return result;
	}

	protected void rebuildWidgets() { clearWidgets(); init(); }

	@Override
	public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 18, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
