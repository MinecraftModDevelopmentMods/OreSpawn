package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Dimension-level entry point for biome placement and world materials. */
final class BiomeWorldMaterialsScreen extends OreSpawnScreen {
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
		addRenderableWidget(CycleButton.builder(this::dimensionName, dimension)
				.withValues(dimensions)
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						OreSpawnScreenLayout.tooltip(Arrays.asList(
								Component.literal(value),
								Component.translatable("tooltip.orespawn.biome.dimension")))))
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
						Component.translatable("tooltip.orespawn.biome.palette_enabled")))
				.create(left, y, contentWidth, 20,
						Component.translatable("option.orespawn.biome_palette"),
						(button, value) -> setPaletteEnabled(value)));
		y += 26;
		addRenderableWidget(CycleButton.builder(this::modeName, mode)
				.withValues(Arrays.asList("augment", "replace"))
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("tooltip.orespawn.biome.mode")))
				.create(left, y, half, 20,
						Component.translatable("option.orespawn.biome_mode"),
						(button, value) -> setPalette("mode", value)));
		addRenderableWidget(CycleButton.builder(this::scopeName, scope)
				.withValues(Arrays.asList("all", "minecraft_only", "selected_namespaces"))
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("tooltip.orespawn.biome.scope")))
				.create(left + half + 5, y, half, 20,
						Component.translatable("option.orespawn.biome_scope"),
						(button, value) -> setPalette("scope", value)));
		y += 26;
		addRenderableWidget(CycleButton.builder(this::regionName, size)
				.withValues(Arrays.asList("tiny", "small", "average", "large", "huge"))
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("tooltip.orespawn.biome.region_size")))
				.create(left, y, contentWidth, 20,
						Component.translatable("option.orespawn.biome_region_size"),
						(button, value) -> setPalette("region_size", value)));
		y += 30;
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, y, half, 20,
				Component.translatable("button.orespawn.biome_palette_count",
						session.biomePlacementIds(dimension).size()),
				button -> minecraft.gui.setScreen(new BiomePaletteScreen(this, session, dimension))),
				"tooltip.orespawn.biome.entries"));
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left + half + 5, y, half, 20,
				Component.translatable("button.orespawn.dimension_materials"),
				button -> minecraft.gui.setScreen(new DimensionMaterialsScreen(this, session, dimension))),
				"tooltip.orespawn.biome.dimension_materials"));
		y += 26;
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, y, contentWidth, 20,
				Component.translatable("button.orespawn.geome_influences"),
				button -> minecraft.gui.setScreen(new GeomeBiomeScreen(this, session))),
				"tooltip.orespawn.biome.geome_influences"));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, OreSpawnScreenLayout.footerY(height),
				150, 20, CommonComponents.GUI_DONE, button -> onClose()));
	}

	private void setPaletteEnabled(boolean enabled) {
		JsonObject palette = session.biomePalette(dimension, true);
		if (enabled && session.biomePlacementIds(dimension).isEmpty()) {
			minecraft.gui.setScreen(new BiomePickerScreen(this, session,
					id -> {
						session.addBiomePlacement(dimension, id);
						minecraft.gui.setScreen(new BiomePlacementScreen(this, session, dimension, id));
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
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		graphics.centeredText(font, title, width / 2, 14, OreSpawnScreenLayout.TEXT_PRIMARY);

	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}
}
