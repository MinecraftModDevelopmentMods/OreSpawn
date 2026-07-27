package com.mcmoddev.orespawn.client;

import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Dimension-level entry point for biome placement and world materials. */
final class BiomeWorldMaterialsScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private String dimension;

	BiomeWorldMaterialsScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.biomes_world_materials"));
		this.parent = parent;
		this.session = session;
		this.dimension = session.availableDimensionIds().get(0);
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 5) / 2;
		List<String> dimensions = session.availableDimensionIds();
		if (!dimensions.contains(dimension)) dimension = dimensions.get(0);
		addRenderableWidget(CycleButton.builder(this::dimensionName)
				.withValues(dimensions).withInitialValue(dimension)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.literal(value)))
				.create(left, 34, contentWidth, 20,
						Component.translatable("option.orespawn.dimension"),
						(button, value) -> { dimension = value; rebuildWidgets(); }));

		JsonObject palette = session.biomePalette(dimension, false);
		boolean active = palette != null && bool(palette, "enabled", false);
		String mode = palette == null ? "augment" : string(palette, "mode", "augment");
		String scope = palette == null ? "minecraft_only" : string(palette, "scope", "minecraft_only");
		String size = palette == null ? "average" : string(palette, "region_size", "average");
		int y = 64;
		addRenderableWidget(CycleButton.onOffBuilder(active)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("guide.orespawn.biomes.1")))
				.create(left, y, contentWidth, 20,
						Component.translatable("option.orespawn.biome_palette"),
						(button, value) -> setPaletteEnabled(value)));
		y += 26;
		addRenderableWidget(CycleButton.builder(this::modeName)
				.withValues(Arrays.asList("augment", "replace")).withInitialValue(mode)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("guide.orespawn.biomes.1")))
				.create(left, y, half, 20,
						Component.translatable("option.orespawn.biome_mode"),
						(button, value) -> setPalette("mode", value)));
		addRenderableWidget(CycleButton.builder(this::scopeName)
				.withValues(Arrays.asList("all", "minecraft_only", "selected_namespaces"))
				.withInitialValue(scope)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("guide.orespawn.biomes.2")))
				.create(left + half + 5, y, half, 20,
						Component.translatable("option.orespawn.biome_scope"),
						(button, value) -> setPalette("scope", value)));
		y += 26;
		addRenderableWidget(CycleButton.builder(this::regionName)
				.withValues(Arrays.asList("tiny", "small", "average", "large", "huge"))
				.withInitialValue(size)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("guide.orespawn.biomes.3")))
				.create(left, y, contentWidth, 20,
						Component.translatable("option.orespawn.biome_region_size"),
						(button, value) -> setPalette("region_size", value)));
		y += 30;
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, y, half, 20,
				Component.translatable("button.orespawn.biome_palette_count",
						session.biomePlacementIds(dimension).size()),
				button -> minecraft.setScreen(new BiomePaletteScreen(this, session, dimension))),
				"guide.orespawn.biomes.3"));
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left + half + 5, y, half, 20,
				Component.translatable("button.orespawn.dimension_materials"),
				button -> minecraft.setScreen(new DimensionMaterialsScreen(this, session, dimension))),
				"guide.orespawn.materials.1"));
		y += 26;
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, y, contentWidth, 20,
				Component.translatable("button.orespawn.geome_influences"),
				button -> minecraft.setScreen(new GeomeBiomeScreen(this, session))),
				"guide.orespawn.rocks.1"));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, OreSpawnScreenLayout.footerY(height),
				150, 20, CommonComponents.GUI_DONE, button -> onClose()));
	}

	private void setPaletteEnabled(boolean enabled) {
		JsonObject palette = session.biomePalette(dimension, true);
		if (enabled && session.biomePlacementIds(dimension).isEmpty()) {
			minecraft.setScreen(new BiomePickerScreen(this, session,
					id -> {
						session.addBiomePlacement(dimension, id);
						minecraft.setScreen(new BiomePlacementScreen(this, session, dimension, id));
					}));
			return;
		}
		palette.addProperty("enabled", enabled);
	}

	private void setPalette(String key, String value) {
		session.biomePalette(dimension, true).addProperty(key, value);
	}

	private Component dimensionName(String value) {
		return Component.literal(value);
	}

	private Component modeName(String value) {
		return Component.translatable("value.orespawn.biome_mode." + value);
	}

	private Component scopeName(String value) {
		return Component.translatable("value.orespawn.biome_scope." + value);
	}

	private Component regionName(String value) {
		return Component.translatable("value.orespawn.preset." + value);
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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 14, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}
}
