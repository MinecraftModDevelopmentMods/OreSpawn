package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Dimension-level entry point for biome placement and world materials. */
final class BiomeWorldMaterialsScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private String dimension;

	BiomeWorldMaterialsScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslationTextComponent("screen.orespawn.biomes_world_materials"));
		this.parent = parent;
		this.session = session;
		this.dimension = session.availableDimensionIds().get(0);
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 5) / 2;
		List<String> dimensions = session.availableDimensionIds();
		if (!dimensions.contains(dimension)) dimension = dimensions.get(0);
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::dimensionName)
				.withValues(dimensions).withInitialValue(dimension)
				.create(left, 34, contentWidth, 20,
						new TranslationTextComponent("option.orespawn.dimension"),
						(button, value) -> { dimension = value; rebuildWidgets(); })),
				"tooltip.orespawn.biome.dimension");

		JsonObject palette = session.biomePalette(dimension, false);
		boolean active = palette != null && bool(palette, "enabled", false);
		String mode = palette == null ? "augment" : string(palette, "mode", "augment");
		String scope = palette == null ? "minecraft_only" : string(palette, "scope", "minecraft_only");
		String size = palette == null ? "average" : string(palette, "region_size", "average");
		int y = 64;
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.onOffBuilder(active)
				.create(left, y, contentWidth, 20,
						new TranslationTextComponent("option.orespawn.biome_palette"),
						(button, value) -> setPaletteEnabled(value))),
				"tooltip.orespawn.biome.palette_enabled");
		y += 26;
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::modeName)
				.withValues(Arrays.asList("augment", "replace")).withInitialValue(mode)
				.create(left, y, half, 20,
						new TranslationTextComponent("option.orespawn.biome_mode"),
						(button, value) -> setPalette("mode", value))),
				"tooltip.orespawn.biome.mode");
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::scopeName)
				.withValues(Arrays.asList("all", "minecraft_only", "selected_namespaces"))
				.withInitialValue(scope)
				.create(left + half + 5, y, half, 20,
						new TranslationTextComponent("option.orespawn.biome_scope"),
						(button, value) -> setPalette("scope", value))),
				"tooltip.orespawn.biome.scope");
		y += 26;
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::regionName)
				.withValues(Arrays.asList("tiny", "small", "average", "large", "huge"))
				.withInitialValue(size)
				.create(left, y, contentWidth, 20,
						new TranslationTextComponent("option.orespawn.biome_region_size"),
						(button, value) -> setPalette("region_size", value))),
				"tooltip.orespawn.biome.region_size");
		y += 30;
		addButton(OreSpawnScreenLayout.explainedButton(this, font, left, y, half, 20,
				new TranslationTextComponent("button.orespawn.biome_palette_count",
						session.biomePlacementIds(dimension).size()),
				button -> minecraft.setScreen(new BiomePaletteScreen(this, session, dimension)),
				"tooltip.orespawn.biome.entries"));
		addButton(OreSpawnScreenLayout.explainedButton(this, font, left + half + 5, y, half, 20,
				new TranslationTextComponent("button.orespawn.dimension_materials"),
				button -> minecraft.setScreen(new DimensionMaterialsScreen(this, session, dimension)),
				"tooltip.orespawn.biome.dimension_materials"));
		y += 26;
		addButton(OreSpawnScreenLayout.explainedButton(this, font, left, y, contentWidth, 20,
				new TranslationTextComponent("button.orespawn.geome_influences"),
				button -> minecraft.setScreen(new GeomeBiomeScreen(this, session)),
				"tooltip.orespawn.biome.geome_influences"));
		addButton(new Button(width / 2 - 75, OreSpawnScreenLayout.footerY(height),
				150, 20, DialogTexts.GUI_DONE, button -> onClose()));
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

	private ITextComponent dimensionName(String value) {
		return new StringTextComponent(value);
	}

	private ITextComponent modeName(String value) {
		return new TranslationTextComponent("value.orespawn.biome_mode." + value);
	}

	private ITextComponent scopeName(String value) {
		return new TranslationTextComponent("value.orespawn.biome_scope." + value);
	}

	private ITextComponent regionName(String value) {
		return new TranslationTextComponent("value.orespawn.preset." + value);
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 14, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}
}
