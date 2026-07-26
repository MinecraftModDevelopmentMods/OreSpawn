package com.mcmoddev.orespawn.client;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class BiomePlacementScreen extends Screen {
	private enum Tab { PLACEMENT, CLIMATE, SURFACE }

	private final Screen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private final String biomeId;
	private Tab tab = Tab.PLACEMENT;
	private EditBox weight;
	private EditBox minTemperature;
	private EditBox maxTemperature;
	private EditBox minDownfall;
	private EditBox maxDownfall;
	private EditBox fillerDepth;

	BiomePlacementScreen(Screen parent, GeologyEditorSession session,
			String dimension, String biomeId) {
		super(Component.translatable("screen.orespawn.biome_placement"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
		this.biomeId = biomeId;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(290, width - 24));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 5) / 2;
		int tabWidth = (contentWidth - 10) / 3;
		for (int i = 0; i < Tab.values().length; i++) {
			Tab value = Tab.values()[i];
			Button button = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + i * (tabWidth + 5), 40,
					tabWidth, 20, Component.translatable("tab.orespawn.biome_"
							+ value.name().toLowerCase(java.util.Locale.ROOT)),
					selected -> { saveFields(); tab = value; rebuildWidgets(); }));
			button.active = value != tab;
		}
		JsonObject placement = session.biomePlacement(dimension, biomeId);
		addRenderableWidget(CycleButton.onOffBuilder(bool(placement, "enabled", true))
				.create(left, 64, contentWidth, 20,
						Component.translatable("option.orespawn.enabled"),
						(button, value) -> placement.addProperty("enabled", value)));
		if (tab == Tab.PLACEMENT) initPlacement(left, half, placement);
		else if (tab == Tab.CLIMATE) initClimate(left, half, placement);
		else initSurface(left, contentWidth, placement);
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, OreSpawnScreenLayout.footerY(height),
				half, 20, CommonComponents.GUI_DONE, button -> { saveFields(); onClose(); }));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + half + 5, OreSpawnScreenLayout.footerY(height),
				half, 20, Component.translatable("button.orespawn.remove"), button -> {
					session.removeBiomePlacement(dimension, biomeId);
					onClose();
				}));
	}

	private void initPlacement(int left, int half, JsonObject placement) {
		int y = 90;
		weight = field(left + half + 5, y, half, decimal(placement, "weight", 1.0D));
		label(left, y + 6, half, "option.orespawn.weight");
		y += 28;
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, y, half, 20,
				Component.translatable("button.orespawn.similar_biomes",
						array(placement, "similar_biomes").size()),
				button -> { saveFields(); minecraft.setScreen(new BiomeReferenceScreen(
						this, session, placement, "similar_biomes")); }));
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, left + half + 5, y,
				half, 20, Component.translatable("button.orespawn.required_biomes",
						array(placement, "required_similar_biomes").size()),
				button -> { saveFields(); minecraft.setScreen(new BiomeReferenceScreen(
						this, session, placement, "required_similar_biomes")); }));
	}

	private void initClimate(int left, int half, JsonObject placement) {
		int y = 90;
		minTemperature = field(left + half + 5, y, half,
				decimal(placement, "min_temperature", -2.0D));
		label(left, y + 6, half, "option.orespawn.min_temperature");
		y += 24;
		maxTemperature = field(left + half + 5, y, half,
				decimal(placement, "max_temperature", 2.0D));
		label(left, y + 6, half, "option.orespawn.max_temperature");
		y += 24;
		minDownfall = field(left + half + 5, y, half,
				decimal(placement, "min_downfall", 0.0D));
		label(left, y + 6, half, "option.orespawn.min_downfall");
		y += 24;
		maxDownfall = field(left + half + 5, y, half,
				decimal(placement, "max_downfall", 1.0D));
		label(left, y + 6, half, "option.orespawn.max_downfall");
	}

	private void initSurface(int left, int width, JsonObject placement) {
		JsonObject surface = object(placement, "surface");
		int y = 90;
		for (String key : Arrays.asList("top_block", "filler_block",
				"underwater_block", "ceiling_block")) {
			String current = string(surface, key, "");
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, y,
					width - 65, 20, materialLabel(key, current), button -> {
						saveFields();
						minecraft.setScreen(new MaterialBlockPickerScreen(this, session,
								false, id -> surface.addProperty(key, id)));
					}));
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left + width - 60, y, 60, 20,
					Component.translatable("button.orespawn.clear"),
					button -> { surface.remove(key); rebuildWidgets(); }));
			y += 24;
		}
		fillerDepth = field(left + width / 2, y, width / 2,
				integer(surface, "filler_depth", 3));
		label(left, y + 6, width / 2 - 5, "option.orespawn.filler_depth");
	}

	private Component materialLabel(String key, String value) {
		return Component.translatable("option.orespawn." + key,
				value.isEmpty() ? Component.translatable("value.orespawn.not_set")
						: Component.literal(value));
	}

	private EditBox field(int x, int y, int width, double value) {
		EditBox result = addRenderableWidget(new EditBox(font, x, y, width, 20, Component.empty()));
		result.setValue(Double.toString(value));
		return result;
	}

	private void label(int x, int y, int labelWidth, String key) {
		addRenderableWidget(OreSpawnScreenLayout.plainButton(x, y - 6, Math.max(1, labelWidth), 20,
				Component.translatable(key), button -> { })).active = false;
	}

	private void saveFields() {
		JsonObject placement = session.biomePlacement(dimension, biomeId);
		if (weight != null) putDouble(placement, "weight", weight, 0.0D, 1000.0D);
		if (minTemperature != null) putDouble(placement, "min_temperature", minTemperature, -2.0D, 2.0D);
		if (maxTemperature != null) putDouble(placement, "max_temperature", maxTemperature, -2.0D, 2.0D);
		if (minDownfall != null) putDouble(placement, "min_downfall", minDownfall, 0.0D, 1.0D);
		if (maxDownfall != null) putDouble(placement, "max_downfall", maxDownfall, 0.0D, 1.0D);
		if (fillerDepth != null) {
			try {
				int value = Integer.parseInt(fillerDepth.getValue().trim());
				object(placement, "surface").addProperty("filler_depth",
						Math.max(0, Math.min(16, value)));
			} catch (NumberFormatException ignored) { }
		}
	}

	private static void putDouble(JsonObject root, String key, EditBox field,
			double min, double max) {
		try {
			double value = Double.parseDouble(field.getValue().trim());
			if (Double.isFinite(value)) root.addProperty(key, Math.max(min, Math.min(max, value)));
		} catch (NumberFormatException ignored) { }
	}

	protected void rebuildWidgets() { clearWidgets(); init(); }
	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);
		drawCenteredString(poseStack, font, Component.literal(biomeId), width / 2, 28, 0xCCCCCC);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private static JsonObject object(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key, new JsonObject());
		return root.getAsJsonObject(key);
	}
	private static JsonArray array(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonArray()) root.add(key, new JsonArray());
		return root.getAsJsonArray(key);
	}
	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}
	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}
	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}
	private static double decimal(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}
}
