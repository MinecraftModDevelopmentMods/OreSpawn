package com.mcmoddev.orespawn.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class DimensionMaterialsScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private EditBox deepY;

	DimensionMaterialsScreen(Screen parent, GeologyEditorSession session, String dimension) {
		super(Component.translatable("screen.orespawn.dimension_materials"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(290, width - 24));
		int left = (width - contentWidth) / 2;
		JsonObject materials = session.dimensionMaterials(dimension, true);
		int y = 58;
		y = materialRow(left, y, contentWidth, materials, "default_fluid", true);
		y = materialRow(left, y, contentWidth, materials, "deep_aquifer_fluid", true);
		deepY = addRenderableWidget(new EditBox(font, left + contentWidth / 2, y,
				contentWidth / 2, 20, Component.translatable("option.orespawn.deep_aquifer_y")));
		deepY.setValue(Integer.toString(integer(materials, "deep_aquifer_max_y", -54)));
		OreSpawnScreenLayout.explain(deepY, "guide.orespawn.materials.2");
		Button deepLabel = addRenderableWidget(OreSpawnScreenLayout.plainButton(
				left, y, contentWidth / 2 - 5, 20,
				Component.translatable("option.orespawn.deep_aquifer_y"), button -> { }));
		deepLabel.active = false;
		y += 26;
		y = materialRow(left, y, contentWidth, materials, "snow_block", false);
		materialRow(left, y, contentWidth, materials, "ice_block", false);
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, OreSpawnScreenLayout.footerY(height),
				contentWidth, 20, CommonComponents.GUI_DONE, button -> { save(); onClose(); }));
	}

	private int materialRow(int left, int y, int width, JsonObject materials,
			String key, boolean fluid) {
		String value = string(materials, key, "");
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, y, width - 65, 20,
				label(key, value), button -> {
					save();
					minecraft.setScreen(new MaterialBlockPickerScreen(this, session, fluid,
							id -> session.setMaterialBlock(dimension, key, id, fluid)));
				}), materialHelp(key)));
		Button clear = addRenderableWidget(OreSpawnScreenLayout.plainButton(
				left + width - 60, y, 60, 20,
				Component.translatable("button.orespawn.clear"), button -> {
					session.setMaterialBlock(dimension, key, null, fluid);
					rebuildWidgets();
				}));
		clear.active = !value.isEmpty();
		return y + 26;
	}

	private static String materialHelp(String key) {
		return switch (key) {
			case "default_fluid", "deep_aquifer_fluid" -> "guide.orespawn.materials.2";
			case "snow_block", "ice_block" -> "guide.orespawn.materials.3";
			default -> "guide.orespawn.materials.1";
		};
	}

	private Component label(String key, String value) {
		return Component.translatable("option.orespawn." + key,
				value.isEmpty() ? Component.translatable("value.orespawn.not_set")
						: Component.literal(value));
	}

	private void save() {
		if (deepY == null) return;
		try {
			session.dimensionMaterials(dimension, true).addProperty("deep_aquifer_max_y",
					Integer.parseInt(deepY.getValue().trim()));
		} catch (NumberFormatException ignored) { }
	}

	protected void rebuildWidgets() { clearWidgets(); init(); }
	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(poseStack, font, Component.literal(dimension), width / 2, 30, 0xCCCCCC);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}
	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}
}
