package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Edits the complete set of controls already supported by one biome palette. */
final class BiomePaletteSettingsScreen extends OreSpawnScreen {
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String paletteId;
	private TextFieldWidget coverage;
	private TextFieldWidget fallback;
	private TextFieldWidget included;
	private TextFieldWidget excluded;

	BiomePaletteSettingsScreen(GuiScreen parent, GeologyEditorSession session, String paletteId) {
		super(new TextComponentTranslation("screen.orespawn.biome_palette_settings"));
		this.parent = parent;
		this.session = session;
		this.paletteId = paletteId;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		JsonObject palette = session.biomePaletteById(paletteId);
		if (palette == null) { onClose(); return; }
		int contentWidth = Math.min(430, Math.max(300, width - 20));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 4) / 2;
		int y = 38;
		addButton(CycleButton.onOffBuilder(bool(palette, "enabled", true)).create(
				left, y, contentWidth, 20, new TextComponentTranslation("option.orespawn.enabled"),
				(button, value) -> palette.addProperty("enabled", value)));
		y += 24;
		addButton(CycleButton.builder(this::modeName)
				.withValues(Arrays.asList("augment", "replace"))
				.withInitialValue(string(palette, "mode", "augment"))
				.create(left, y, half, 20, new TextComponentTranslation("option.orespawn.biome_mode"),
						(button, value) -> palette.addProperty("mode", value)));
		addButton(CycleButton.builder(this::scopeName)
				.withValues(Arrays.asList("all", "minecraft_only", "selected_namespaces"))
				.withInitialValue(string(palette, "scope", "minecraft_only"))
				.create(left + half + 4, y, half, 20,
						new TextComponentTranslation("option.orespawn.biome_scope"),
						(button, value) -> palette.addProperty("scope", value)));
		y += 24;
		addButton(CycleButton.builder(this::regionName)
				.withValues(Arrays.asList("tiny", "small", "average", "large", "huge"))
				.withInitialValue(string(palette, "region_size", "average"))
				.create(left, y, contentWidth, 20,
						new TextComponentTranslation("option.orespawn.biome_region_size"),
						(button, value) -> palette.addProperty("region_size", value)));
		y += 24;
		coverage = field(left, y, half, decimal(palette, "coverage", 1.0D),
				"option.orespawn.biome.coverage");
		fallback = field(left + half + 4, y, half, decimal(palette, "fallback_weight", 1.0D),
				"option.orespawn.biome.fallback_weight");
		y += 24;
		included = addButton(new TextFieldWidget(font, left, y, contentWidth, 20,
				new TextComponentTranslation("option.orespawn.biome.include_namespaces")));
		included.setValue(join(palette.get("include_namespaces")));
		y += 24;
		excluded = addButton(new TextFieldWidget(font, left, y, contentWidth, 20,
				new TextComponentTranslation("option.orespawn.biome.exclude_namespaces")));
		excluded.setValue(join(palette.get("exclude_namespaces")));
		y += 24;
		Button info = addButton(OreSpawnScreenLayout.explainedButton(this, font, left, y,
				contentWidth, 20, new TextComponentTranslation("label.orespawn.biome.palette_entries",
						session.biomePlacementIdsForPalette(paletteId).size()), button -> { },
				"tooltip.orespawn.biome.palette_entries_read_only"));
		info.enabled = false;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private TextFieldWidget field(int x, int y, int width, double value, String label) {
		TextFieldWidget field = addButton(new TextFieldWidget(font, x, y, width, 20,
				new TextComponentTranslation(label)));
		field.setValue(Double.toString(value));
		return field;
	}

	private void save() {
		JsonObject palette = session.biomePaletteById(paletteId);
		if (palette == null) return;
		putDecimal(palette, "coverage", coverage, 0.0D, 1.0D);
		putDecimal(palette, "fallback_weight", fallback, 0.0D, 1000.0D);
		palette.add("include_namespaces", namespaces(included));
		palette.add("exclude_namespaces", namespaces(excluded));
	}

	private static void putDecimal(JsonObject root, String key, TextFieldWidget field,
			double min, double max) {
		try {
			double value = Double.parseDouble(field.getValue().trim());
			if (Double.isFinite(value)) root.addProperty(key, Math.max(min, Math.min(max, value)));
		} catch (RuntimeException ignored) { }
	}

	private static JsonArray namespaces(TextFieldWidget field) {
		Set<String> values = new LinkedHashSet<>();
		if (field != null) for (String raw : field.getValue().split(",")) {
			String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
			if (value.matches("[a-z][a-z0-9_.-]{0,63}")) values.add(value);
		}
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}

	private static String join(JsonElement element) {
		if (element == null || !element.isJsonArray()) return "";
		StringBuilder result = new StringBuilder();
		for (JsonElement value : element.getAsJsonArray()) {
			if (result.length() > 0) result.append(", ");
			result.append(value.getAsString());
		}
		return result.toString();
	}

	private ITextComponent modeName(String value) {
		return new TextComponentTranslation("value.orespawn.biome_mode." + value);
	}
	private ITextComponent scopeName(String value) {
		return new TextComponentTranslation("value.orespawn.biome_scope." + value);
	}
	private ITextComponent regionName(String value) {
		return new TextComponentTranslation("value.orespawn.preset." + value);
	}

	@Override public void onClose() { save(); minecraft.displayGuiScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		drawCenteredString(font, new TextComponentString(paletteId), width / 2, 22, 0xAAAAAA);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}
	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}
	private static double decimal(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}
}
