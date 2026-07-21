package com.mcmoddev.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

final class FluidDepositEntryScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String depositId;
	private boolean enabled;
	private String outputBlock;
	private int page;

	FluidDepositEntryScreen(Screen parent, GeologyEditorSession session, String depositId) {
		super(new TranslatableComponent("screen.orespawn.fluid_deposit"));
		this.parent = parent;
		this.session = session;
		this.depositId = depositId;
		load();
	}

	private void load() {
		JsonObject deposit = session.fluidDeposit(depositId);
		enabled = GeologyEditorSession.bool(deposit, "enabled", true);
		outputBlock = GeologyEditorSession.string(deposit, "block", "");
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(310, width - 24));
		int left = (width - contentWidth) / 2;
		int column = (contentWidth - 5) / 2;
		JsonObject dimensions = dimensions();
		List<String> ids = new ArrayList<>(dimensions.keySet());
		Collections.sort(ids);
		addRenderableWidget(CycleButton.onOffBuilder(enabled).create(left, 46, column, 20,
				new TranslatableComponent("option.orespawn.enabled"),
				(button, value) -> { enabled = value; session.fluidDeposit(depositId).addProperty("enabled", value); }));
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, left + column + 5, 46, column, 20,
				new TranslatableComponent("button.orespawn.reset"), button -> reset()));

		Button output = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left, 70, contentWidth, 20, fluidName(outputBlock), button -> {
					if (!ids.isEmpty()) minecraft.setScreen(
							new FluidDepositDimensionScreen(this, session, depositId, ids.get(0)));
				}));
		output.active = !ids.isEmpty();

		int listTop = 98;
		int controlsY = height - 52;
		int pickerY = height - 76;
		int pageSize = Math.max(1, (pickerY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, listTop + (i * 24),
					contentWidth, 20, new TextComponent(id), button -> minecraft.setScreen(
							new FluidDepositDimensionScreen(this, session, depositId, id))));
		}
		Button previous = addRenderableWidget(new Button(left, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(left + 50, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		List<String> available = session.availableDimensionIds();
		String selected = available.stream().filter(id -> !dimensions.has(id)).findFirst()
				.orElse(available.get(0));
		addRenderableWidget(CycleButton.builder(this::dimensionName)
				.withValues(available).withInitialValue(selected)
				.withTooltip(value -> font.split(new TextComponent(value), 310))
				.create(left, pickerY, contentWidth, 20,
						new TranslatableComponent("option.orespawn.available_dimension"),
						(button, value) -> addDimension(value)));
		addRenderableWidget(new Button(width / 2 - 155, height - 28, 100, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, width / 2 + 55, height - 28, 100, 20,
				new TranslatableComponent("button.orespawn.remove"), button -> {
					session.removeFluidDeposit(depositId); minecraft.setScreen(parent);
				}));
	}

	private void addDimension(String id) {
		JsonObject dimensions = dimensions();
		if (!dimensions.has(id)) dimensions.add(id, defaultDimension(id));
		minecraft.setScreen(new FluidDepositDimensionScreen(this, session, depositId, id));
	}

	private static JsonObject defaultDimension(String id) {
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", "minecraft:overworld".equals(id) ? -48 : 8);
		rule.addProperty("max_y", "minecraft:the_end".equals(id) ? 192 : 120);
		rule.addProperty("frequency", 0.08D);
		rule.addProperty("min_radius", 5);
		rule.addProperty("max_radius", 12);
		rule.addProperty("min_vertical_radius", 2);
		rule.addProperty("max_vertical_radius", 5);
		rule.addProperty("max_lobes", 4);
		rule.addProperty("min_solid_cover", 2);
		rule.addProperty("min_solid_shell", 1);
		rule.add("host_families", new JsonArray());
		JsonArray blocks = new JsonArray();
		JsonArray tags = new JsonArray();
		if ("minecraft:the_end".equals(id)) blocks.add("minecraft:end_stone");
		else tags.add("minecraft:the_nether".equals(id)
				? "minecraft:base_stone_nether" : "minecraft:stone_ore_replaceables");
		rule.add("host_blocks", blocks);
		rule.add("host_tags", tags);
		rule.add("biome_ids", new JsonArray());
		rule.add("excluded_biome_ids", new JsonArray());
		rule.add("biome_dictionary", new JsonArray());
		rule.add("excluded_biome_dictionary", new JsonArray());
		rule.add("geomes", new JsonObject());
		return rule;
	}

	private JsonObject dimensions() {
		JsonObject deposit = session.fluidDeposit(depositId);
		if (!deposit.has("dimensions") || !deposit.get("dimensions").isJsonObject()) {
			deposit.add("dimensions", new JsonObject());
		}
		return deposit.getAsJsonObject("dimensions");
	}

	private void reset() {
		session.resetEntry("fluid_deposits", depositId);
		load();
		rebuildWidgets();
	}

	private Component fluidName(String id) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
			return block == null ? new TextComponent(id) : new TranslatableComponent(block.getDescriptionId());
		} catch (RuntimeException ignored) { return new TextComponent(id); }
	}

	private Component dimensionName(String id) {
		String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : "";
		return path.isEmpty() ? new TextComponent(id)
				: new TranslatableComponent("value.orespawn.dimension." + path);
	}

	private void rebuildWidgets() { clearWidgets(); init(); }
	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);
		drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font,
				new TextComponent(depositId), Math.min(390, width - 24)), width / 2, 26, 0xAAAAAA);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
