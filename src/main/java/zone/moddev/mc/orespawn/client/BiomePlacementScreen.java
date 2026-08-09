package zone.moddev.mc.orespawn.client;

import java.util.Arrays;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

final class BiomePlacementScreen extends OreSpawnScreen {
	private enum Tab { PLACEMENT, CLIMATE, SURFACE }

	private final Screen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private final String biomeId;
	private Tab tab = Tab.PLACEMENT;
	private TextFieldWidget weight;
	private TextFieldWidget minTemperature;
	private TextFieldWidget maxTemperature;
	private TextFieldWidget minDownfall;
	private TextFieldWidget maxDownfall;
	private TextFieldWidget fillerDepth;

	BiomePlacementScreen(Screen parent, GeologyEditorSession session,
			String dimension, String biomeId) {
		super(new TranslationTextComponent("screen.orespawn.biome_placement"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
		this.biomeId = biomeId;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int contentWidth = Math.min(390, Math.max(290, width - 24));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 5) / 2;
		int tabWidth = (contentWidth - 10) / 3;
		for (int i = 0; i < Tab.values().length; i++) {
			Tab value = Tab.values()[i];
			Button button = addButton(new Button(left + i * (tabWidth + 5), 40,
					tabWidth, 20, new TranslationTextComponent("tab.orespawn.biome_"
							+ value.name().toLowerCase(java.util.Locale.ROOT)),
					selected -> { saveFields(); tab = value; rebuildWidgets(); }));
			button.active = value != tab;
		}
		JsonObject placement = session.biomePlacement(dimension, biomeId);
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.onOffBuilder(
				bool(placement, "enabled", true))
				.create(left, 64, contentWidth, 20,
						new TranslationTextComponent("option.orespawn.enabled"),
						(button, value) -> placement.addProperty("enabled", value))),
				"tooltip.orespawn.enabled");
		if (tab == Tab.PLACEMENT) initPlacement(left, half, placement);
		else if (tab == Tab.CLIMATE) initClimate(left, half, placement);
		else initSurface(left, contentWidth, placement);
		addButton(new Button(left, OreSpawnScreenLayout.footerY(height),
				half, 20, DialogTexts.GUI_DONE, button -> { saveFields(); onClose(); }));
		addButton(new Button(left + half + 5, OreSpawnScreenLayout.footerY(height),
				half, 20, new TranslationTextComponent("button.orespawn.remove"), button -> {
					session.removeBiomePlacement(dimension, biomeId);
					onClose();
				}));
	}

	private void initPlacement(int left, int half, JsonObject placement) {
		int y = 90;
		weight = field(left + half + 5, y, half, decimal(placement, "weight", 1.0D));
		OreSpawnScreenLayout.explain(this, weight, "tooltip.orespawn.weight");
		label(left, y + 6, half, "option.orespawn.weight");
		y += 28;
		addButton(OreSpawnScreenLayout.explainedButton(this, font, left, y, half, 20,
				new TranslationTextComponent("button.orespawn.similar_biomes",
						array(placement, "similar_biomes").size()),
				button -> { saveFields(); minecraft.displayGuiScreen(new BiomeReferenceScreen(
						this, session, placement, "similar_biomes")); },
				"tooltip.orespawn.biome.similar_biomes"));
		addButton(OreSpawnScreenLayout.explainedButton(this, font, left + half + 5, y,
				half, 20, new TranslationTextComponent("button.orespawn.required_biomes",
						array(placement, "required_similar_biomes").size()),
				button -> { saveFields(); minecraft.displayGuiScreen(new BiomeReferenceScreen(
						this, session, placement, "required_similar_biomes")); },
				"tooltip.orespawn.biome.required_similar_biomes"));
	}

	private void initClimate(int left, int half, JsonObject placement) {
		int y = 90;
		minTemperature = field(left + half + 5, y, half,
				decimal(placement, "min_temperature", -2.0D));
		OreSpawnScreenLayout.explain(this, minTemperature, "tooltip.orespawn.biome.min_temperature");
		label(left, y + 6, half, "option.orespawn.min_temperature");
		y += 24;
		maxTemperature = field(left + half + 5, y, half,
				decimal(placement, "max_temperature", 2.0D));
		OreSpawnScreenLayout.explain(this, maxTemperature, "tooltip.orespawn.biome.max_temperature");
		label(left, y + 6, half, "option.orespawn.max_temperature");
		y += 24;
		minDownfall = field(left + half + 5, y, half,
				decimal(placement, "min_downfall", 0.0D));
		OreSpawnScreenLayout.explain(this, minDownfall, "tooltip.orespawn.biome.min_downfall");
		label(left, y + 6, half, "option.orespawn.min_downfall");
		y += 24;
		maxDownfall = field(left + half + 5, y, half,
				decimal(placement, "max_downfall", 1.0D));
		OreSpawnScreenLayout.explain(this, maxDownfall, "tooltip.orespawn.biome.max_downfall");
		label(left, y + 6, half, "option.orespawn.max_downfall");
	}

	private void initSurface(int left, int width, JsonObject placement) {
		JsonObject surface = object(placement, "surface");
		int y = 90;
		for (String key : Arrays.asList("top_block", "filler_block",
				"underwater_block", "ceiling_block")) {
			String current = string(surface, key, "");
			Button material = addButton(OreSpawnScreenLayout.explainedButton(this, font, left, y,
					width - 65, 20, materialLabel(key, current), button -> {
						saveFields();
						minecraft.displayGuiScreen(new MaterialBlockPickerScreen(this, session,
								false, id -> surface.addProperty(key, id)));
					}, "tooltip.orespawn.biome." + key));
			Button clear = addButton(new Button(left + width - 60, y, 60, 20,
					new TranslationTextComponent("button.orespawn.clear"),
					button -> { surface.remove(key); rebuildWidgets(); }));
			clear.active = !current.isEmpty();
			y += 24;
		}
		fillerDepth = field(left + width / 2, y, width / 2,
				integer(surface, "filler_depth", 3));
		OreSpawnScreenLayout.explain(this, fillerDepth, "tooltip.orespawn.biome.filler_depth");
		label(left, y + 6, width / 2 - 5, "option.orespawn.filler_depth");
	}

	private ITextComponent materialLabel(String key, String value) {
		return new TranslationTextComponent("option.orespawn." + key,
				value.isEmpty() ? new TranslationTextComponent("value.orespawn.not_set")
						: new StringTextComponent(value));
	}

	private TextFieldWidget field(int x, int y, int width, double value) {
		TextFieldWidget result = addButton(new TextFieldWidget(font, x, y, width, 20, ""));
		result.setValue(Double.toString(value));
		return result;
	}

	private void label(int x, int y, int labelWidth, String key) {
		addButton(new Button(x, y - 6, Math.max(1, labelWidth), 20,
				new TranslationTextComponent(key), button -> { })).active = false;
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

	private static void putDouble(JsonObject root, String key, TextFieldWidget field,
			double min, double max) {
		try {
			double value = Double.parseDouble(field.getValue().trim());
			if (Double.isFinite(value)) root.addProperty(key, Math.max(min, Math.min(max, value)));
		} catch (NumberFormatException ignored) { }
	}

	private void rebuildWidgets() { buttons.clear(); children.clear(); init(); }
	@Override public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		drawCenteredString(font, new StringTextComponent(biomeId), width / 2, 28, 0xCCCCCC);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
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
