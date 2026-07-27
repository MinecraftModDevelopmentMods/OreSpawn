package zone.moddev.mc.orespawn.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.worldgen.OreHeightDistribution;
import zone.moddev.mc.orespawn.worldgen.OrePattern;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class OreDimensionScreen extends Screen {
	private enum Page { PLACEMENT, PATTERN, HOSTS }

	private final Screen parent;
	private final GeologyEditorSession session;
	private final String oreId;
	private final String dimensionId;
	private final boolean dimensionSelector;
	private final double baselineFrequency;
	private final EnumSet<RockFamily> families = EnumSet.noneOf(RockFamily.class);
	private boolean enabled;
	private EditBox minY;
	private EditBox maxY;
	private EditBox frequency;
	private EditBox minQuantity;
	private EditBox maxQuantity;
	private EditBox discardAirExposure;
	private CycleButton<OreRichnessPreset> richness;
	private EditBox spread;
	private EditBox verticalSpread;
	private EditBox nodeSize;
	private EditBox hostBlocks;
	private EditBox hostTags;
	private OrePattern pattern;
	private boolean externalPattern;
	private String externalPatternId = "";
	private String originalHostBlocksText = "";
	private String originalHostTagsText = "";
	private OreHeightDistribution heightDistribution;
	private Component error;
	private Page page = Page.PLACEMENT;
	private final List<Button> pageButtons = new ArrayList<>();
	private final List<AbstractWidget> placementWidgets = new ArrayList<>();
	private final List<AbstractWidget> patternWidgets = new ArrayList<>();
	private final List<AbstractWidget> hostWidgets = new ArrayList<>();
	private int contentLeft;
	private int contentWidth = 310;
	private int columnWidth = 150;

	OreDimensionScreen(Screen parent, GeologyEditorSession session, String oreId, String dimensionId) {
		super(Component.translatable("screen.orespawn.ore_dimension"));
		this.parent = parent;
		this.session = session;
		this.oreId = oreId;
		this.dimensionId = dimensionId;
		this.dimensionSelector = OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString()
				.equals(dimensionId);
		this.baselineFrequency = session.oreFrequencyBaseline(oreId, dimensionId);
		load();
	}

	private void load() {
		JsonObject rule = rule();
		enabled = GeologyEditorSession.bool(rule, "enabled", true);
		externalPattern = false;
		externalPatternId = "";
		try {
			JsonElement configured = rule.get("pattern");
			if (configured != null && configured.isJsonObject()) {
				externalPatternId = GeologyEditorSession.string(configured.getAsJsonObject(), "type", "orespawn:vein");
				ResourceLocation id = new ResourceLocation(externalPatternId);
				externalPattern = true;
				pattern = "orespawn".equals(id.getNamespace())
						? OrePattern.fromConfigName(id.getPath()) : OrePattern.VEIN;
			} else {
				pattern = OrePattern.fromConfigName(GeologyEditorSession.string(rule, "pattern", "vein"));
			}
		} catch (RuntimeException e) {
			pattern = OrePattern.VEIN;
		}
		try {
			heightDistribution = OreHeightDistribution.fromConfigName(
					GeologyEditorSession.string(rule, "height_distribution", "uniform"));
		} catch (RuntimeException e) {
			heightDistribution = OreHeightDistribution.UNIFORM;
		}
		families.clear();
		if (rule.has("host_families") && rule.get("host_families").isJsonArray()) {
			for (JsonElement value : rule.getAsJsonArray("host_families")) {
				try { families.add(RockFamily.fromConfigName(value.getAsString())); }
				catch (RuntimeException ignored) { }
			}
		}
	}

	@Override
	protected void init() {
		JsonObject rule = rule();
		contentWidth = Math.min(390, Math.max(310, width - 24));
		columnWidth = (contentWidth - 5) / 2;
		contentLeft = (width - contentWidth) / 2;
		int left = contentLeft;
		int right = left + columnWidth + 5;
		pageButtons.clear();
		placementWidgets.clear();
		patternWidgets.clear();
		hostWidgets.clear();
		addRenderableWidget(OreSpawnScreenLayout.explain(
				CycleButton.onOffBuilder(enabled).create(left, 32, contentWidth, 20,
						Component.translatable("option.orespawn.enabled"),
						(button, value) -> enabled = value),
				"guide.orespawn.ores.1"));
		int tabWidth = (contentWidth - 10) / 3;
		pageButtons.add(addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, 56, tabWidth, 20,
				Component.translatable("tab.orespawn.placement"), button -> showPage(Page.PLACEMENT))));
		pageButtons.add(addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left + tabWidth + 5, 56, tabWidth, 20,
				Component.translatable("tab.orespawn.pattern"), button -> showPage(Page.PATTERN))));
		pageButtons.add(addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left + ((tabWidth + 5) * 2), 56, contentWidth - ((tabWidth + 5) * 2), 20,
				Component.translatable("tab.orespawn.hosts"), button -> showPage(Page.HOSTS))));
		double currentFrequency = GeologyEditorSession.decimal(rule, "frequency", baselineFrequency);
		richness = addRenderableWidget(CycleButton.builder(this::richnessName)
				.withValues(Arrays.asList(OreRichnessPreset.values()))
				.withInitialValue(OreRichnessPreset.fromFrequency(baselineFrequency, currentFrequency))
				.withTooltip(value -> tooltip("tooltip.orespawn.ore_richness"))
				.create(left, 80, contentWidth, 20, Component.translatable("option.orespawn.ore_richness"),
						(button, value) -> applyRichness(value)));
		placementWidgets.add(richness);
		int fixedQuantity = GeologyEditorSession.integer(rule, "quantity", 8);
		if (OreSpawnScreenLayout.compact(height)) {
			minY = addPlacementField(left, compactPlacementFieldY(0), "min_y", text(rule, "min_y", -64));
			maxY = addPlacementField(right, compactPlacementFieldY(0), "max_y", text(rule, "max_y", 64));
			frequency = addPlacementField(left, compactPlacementFieldY(1), "frequency",
					text(rule, "frequency", 1.0D));
			discardAirExposure = addPlacementField(right, compactPlacementFieldY(1), "discard_air_exposure",
					text(rule, "discard_chance_on_air_exposure", 0.0D));
			minQuantity = addPlacementField(left, compactPlacementFieldY(2), "min_quantity",
					text(rule, "min_quantity", fixedQuantity));
			maxQuantity = addPlacementField(right, compactPlacementFieldY(2), "max_quantity",
					text(rule, "max_quantity", fixedQuantity));
		} else {
			minY = addPlacementField(right, 104, "min_y", text(rule, "min_y", -64));
			maxY = addPlacementField(right, 128, "max_y", text(rule, "max_y", 64));
			frequency = addPlacementField(right, 152, "frequency", text(rule, "frequency", 1.0D));
			minQuantity = addPlacementField(right, 176, "min_quantity",
					text(rule, "min_quantity", fixedQuantity));
			maxQuantity = addPlacementField(right, 200, "max_quantity",
					text(rule, "max_quantity", fixedQuantity));
			discardAirExposure = addPlacementField(right, 224, "discard_air_exposure",
					text(rule, "discard_chance_on_air_exposure", 0.0D));
		}

		AbstractWidget patternButton;
		if (externalPattern) {
			Button external = OreSpawnScreenLayout.button(this, font, left, 80, contentWidth, 20,
					Component.literal("Pattern: " + externalPatternId), button -> { });
			OreSpawnScreenLayout.explain(external, "guide.orespawn.patterns.1");
			external.active = false;
			patternButton = addRenderableWidget(external);
		} else {
			patternButton = addRenderableWidget(CycleButton.builder(this::patternName)
					.withValues(Arrays.asList(OrePattern.values()))
					.withInitialValue(pattern)
					.withTooltip(value -> tooltip("guide.orespawn.patterns.1"))
					.create(left, 80, contentWidth, 20, Component.translatable("option.orespawn.pattern"),
							(button, value) -> {
								pattern = value;
								updatePatternControls();
							}));
		}
		patternWidgets.add(patternButton);
		patternWidgets.add(addRenderableWidget(CycleButton.builder(this::distributionName)
				.withValues(Arrays.asList(OreHeightDistribution.values()))
				.withInitialValue(heightDistribution)
				.withTooltip(value -> tooltip("guide.orespawn.patterns.2"))
				.create(left, 104, contentWidth, 20,
						Component.translatable("option.orespawn.height_distribution"),
						(button, value) -> heightDistribution = value)));
		spread = addPatternField(right, 128, "spread", text(rule, "spread", 8));
		verticalSpread = addPatternField(right, 152, "vertical_spread", text(rule, "vertical_spread", 4));
		nodeSize = addPatternField(right, 176, "node_size", text(rule, "node_size", 4));

		originalHostBlocksText = join(rule.get("host_blocks"), "block");
		originalHostTagsText = join(rule.get("host_tags"), "tag");
		hostBlocks = addHostField(left, 88, "host_blocks", originalHostBlocksText);
		hostTags = addHostField(left, 120, "host_tags", originalHostTagsText);
		Button weights = addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left, 144, columnWidth, 20,
				Component.translatable("button.orespawn.geome_weights"), button -> openWeights()));
		OreSpawnScreenLayout.explain(weights, "guide.orespawn.ores.3");
		weights.active = "minecraft:overworld".equals(dimensionId) || dimensionSelector;
		hostWidgets.add(weights);
		hostWidgets.add(addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				right, 144, columnWidth, 20,
				Component.translatable("button.orespawn.remove_dimension"), button -> removeDimension())));

		RockFamily[] values = RockFamily.values();
		for (int i = 0; i < values.length; i++) {
			RockFamily family = values[i];
			int x = (i & 1) == 0 ? left : right;
			int y = 168 + ((i / 2) * 22);
			hostWidgets.add(addRenderableWidget(OreSpawnScreenLayout.explain(
					CycleButton.onOffBuilder(families.contains(family)).create(x, y, columnWidth, 20,
					Component.translatable("value.orespawn.family." + family.configName),
					(button, selected) -> {
						if (selected) families.add(family); else families.remove(family);
					}), "guide.orespawn.ores.3")));
		}

		int bottom = height - 28;
		addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				left, bottom, columnWidth, 20, CommonComponents.GUI_DONE,
				button -> saveAndClose()));
		addRenderableWidget(OreSpawnScreenLayout.button(this, font,
				right, bottom, columnWidth, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
		showPage(page);
		updatePatternControls();
	}

	private EditBox addPlacementField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, columnWidth, 20, Component.literal(key));
		box.setValue(value);
		box.setMaxLength(32);
		OreSpawnScreenLayout.explain(box, placementHelp(key));
		placementWidgets.add(addRenderableWidget(box));
		return box;
	}

	private int compactPlacementFieldY(int row) {
		return OreSpawnScreenLayout.compactOrePlacementFieldY(height, row);
	}

	private EditBox addHostField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, contentWidth, 20, Component.literal(key));
		box.setValue(value);
		box.setMaxLength(1024);
		OreSpawnScreenLayout.explain(box, "guide.orespawn.ores.3");
		hostWidgets.add(addRenderableWidget(box));
		return box;
	}

	private EditBox addPatternField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, columnWidth, 20, Component.literal(key));
		box.setValue(value);
		box.setMaxLength(32);
		OreSpawnScreenLayout.explain(box, "guide.orespawn.patterns.1");
		patternWidgets.add(addRenderableWidget(box));
		return box;
	}

	private static String placementHelp(String key) {
		return "discard_air_exposure".equals(key)
				? "guide.orespawn.patterns.3" : "guide.orespawn.ores.2";
	}

	private void showPage(Page selected) {
		page = selected;
		for (int i = 0; i < pageButtons.size(); i++) {
			pageButtons.get(i).active = i != selected.ordinal();
		}
		for (AbstractWidget widget : placementWidgets) widget.visible = selected == Page.PLACEMENT;
		for (AbstractWidget widget : patternWidgets) widget.visible = selected == Page.PATTERN;
		for (AbstractWidget widget : hostWidgets) widget.visible = selected == Page.HOSTS;
	}

	private void updatePatternControls() {
		if (spread != null) {
			boolean usesSpread = !externalPattern && pattern != OrePattern.VEIN;
			spread.active = usesSpread;
			verticalSpread.active = usesSpread;
			nodeSize.active = !externalPattern && pattern == OrePattern.CLUSTERS;
		}
	}

	private void applyRichness(OreRichnessPreset preset) {
		frequency.setValue(format(preset.scaledFrequency(baselineFrequency)));
		error = null;
	}

	private void openWeights() {
		if (!save()) return;
		JsonObject rule = rule();
		JsonObject weights = rule.has("geomes") && rule.get("geomes").isJsonObject()
				? rule.getAsJsonObject("geomes") : new JsonObject();
		rule.add("geomes", weights);
		minecraft.setScreen(new WeightMapScreen(this, Component.translatable("screen.orespawn.geome_weights"),
				weights, session.geomeIds(), 1.0D));
	}

	private void saveAndClose() {
		if (save()) minecraft.setScreen(parent);
	}

	private boolean save() {
		try {
			int parsedMin = integer(minY, -2048, 2048);
			int parsedMax = integer(maxY, -2048, 2048);
			double parsedFrequency = number(frequency, 0.0D, 64.0D);
			int parsedMinQuantity = integer(minQuantity, 1, 64);
			int parsedMaxQuantity = integer(maxQuantity, 1, 64);
			double parsedDiscardAirExposure = number(discardAirExposure, 0.0D, 1.0D);
			int parsedSpread = integer(spread, 0, 64);
			int parsedVerticalSpread = integer(verticalSpread, 0, 64);
			int parsedNodeSize = integer(nodeSize, 1, 32);
			if (parsedMin > parsedMax || parsedMinQuantity > parsedMaxQuantity) throw new NumberFormatException();
			JsonArray blocks = ids(hostBlocks.getValue());
			JsonArray tags = ids(hostTags.getValue());
			if (enabled && families.isEmpty() && blocks.size() == 0 && tags.size() == 0) {
				error = Component.literal("An enabled dimension needs at least one host.");
				return false;
			}
			JsonObject rule = rule();
			rule.addProperty("enabled", enabled);
			rule.addProperty("min_y", parsedMin);
			rule.addProperty("max_y", parsedMax);
			rule.addProperty("frequency", parsedFrequency);
			if (parsedMinQuantity == parsedMaxQuantity) {
				rule.addProperty("quantity", parsedMinQuantity);
				rule.remove("min_quantity");
				rule.remove("max_quantity");
			} else {
				rule.remove("quantity");
				rule.addProperty("min_quantity", parsedMinQuantity);
				rule.addProperty("max_quantity", parsedMaxQuantity);
			}
			rule.addProperty("discard_chance_on_air_exposure", parsedDiscardAirExposure);
			if (!externalPattern) rule.addProperty("pattern", pattern.configName);
			rule.addProperty("height_distribution", heightDistribution.configName);
			if (!externalPattern) {
				rule.addProperty("spread", parsedSpread);
				rule.addProperty("vertical_spread", parsedVerticalSpread);
				rule.addProperty("node_size", parsedNodeSize);
			}
			JsonArray familyArray = new JsonArray();
			for (RockFamily family : RockFamily.values()) {
				if (families.contains(family)) familyArray.add(family.configName);
			}
			rule.add("host_families", familyArray);
			if (!hostBlocks.getValue().trim().equals(originalHostBlocksText)) rule.add("host_blocks", blocks);
			if (!hostTags.getValue().trim().equals(originalHostTagsText)) rule.add("host_tags", tags);
			error = null;
			return true;
		} catch (RuntimeException e) {
			error = Component.literal("Check the numeric ranges and registry IDs.");
			return false;
		}
	}

	private void removeDimension() {
		JsonObject ore = session.ore(oreId);
		String section = dimensionSelector ? "dimension_selectors" : "dimensions";
		if (ore.has(section) && ore.get(section).isJsonObject()) {
			ore.getAsJsonObject(section).remove(dimensionId);
		}
		minecraft.setScreen(parent);
	}

	private JsonObject rule() {
		JsonObject ore = session.ore(oreId);
		String section = dimensionSelector ? "dimension_selectors" : "dimensions";
		if (!ore.has(section) || !ore.get(section).isJsonObject()) {
			ore.add(section, new JsonObject());
		}
		JsonObject dimensions = ore.getAsJsonObject(section);
		if (!dimensions.has(dimensionId) || !dimensions.get(dimensionId).isJsonObject()) {
			dimensions.add(dimensionId, GeologyEditorSession.defaultOreDimension());
		}
		return dimensions.getAsJsonObject(dimensionId);
	}

	private static JsonArray ids(String value) {
		JsonArray result = new JsonArray();
		for (String token : value.split(",")) {
			String id = token.trim();
			if (id.isEmpty()) continue;
			new ResourceLocation(id);
			result.add(id);
		}
		return result;
	}

	private static String join(JsonElement element, String objectKey) {
		if (element == null || !element.isJsonArray()) return "";
		StringBuilder result = new StringBuilder();
		for (JsonElement value : element.getAsJsonArray()) {
			if (result.length() > 0) result.append(", ");
			result.append(value.isJsonObject()
					? GeologyEditorSession.string(value.getAsJsonObject(), objectKey, "")
					: value.getAsString());
		}
		return result.toString();
	}

	private static String text(JsonObject json, String key, Number fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback.toString();
	}

	private static String format(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	private static double number(EditBox box, double min, double max) {
		double value = Double.parseDouble(box.getValue().trim());
		if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
		return value;
	}

	private static int integer(EditBox box, int min, int max) {
		double value = number(box, min, max);
		if (value != Math.rint(value)) throw new NumberFormatException();
		return (int) value;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 2, 0xFFFFFF);
		Component blockName = Component.literal(
				session.materialBlockId(GeologyEditorSession.MaterialTab.ORES, oreId));
		drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font, blockName, contentWidth),
				width / 2, 13, 0xDDDDDD);
		Component dimensionName = dimensionSelector
				? Component.translatable("value.orespawn.dimension.all_except_nether_end")
				: Component.literal(dimensionId);
		boolean compact = OreSpawnScreenLayout.compact(height);
		if (error == null || !compact) {
			drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font, dimensionName, contentWidth),
					width / 2, 23, 0xAAAAAA);
		}
		if (page == Page.PLACEMENT) {
			if (compact) {
				drawCompactPlacementLabels(poseStack);
			} else {
				String[] labels = { "min_y", "max_y", "frequency", "min_quantity", "max_quantity",
						"discard_air_exposure" };
				for (int i = 0; i < labels.length; i++) {
					Component label = Component.translatable("option.orespawn." + labels[i]);
					drawString(poseStack, font, OreSpawnScreenLayout.fit(font, label, columnWidth - 5),
							contentLeft, 110 + (i * 24), 0xDDDDDD);
				}
			}
		} else if (page == Page.PATTERN && !externalPattern) {
			String[] labels = { "spread", "vertical_spread", "node_size" };
			for (int i = 0; i < labels.length; i++) {
				drawString(poseStack, font, Component.translatable("option.orespawn." + labels[i]),
						contentLeft, 134 + (i * 24), 0xDDDDDD);
			}
		} else if (page == Page.PATTERN) {
			drawCenteredString(poseStack, font,
					Component.translatable("message.orespawn.external_pattern_read_only"),
					width / 2, 132, 0xAAAAAA);
		} else {
			drawString(poseStack, font, Component.translatable("option.orespawn.host_blocks"),
					contentLeft, 78, 0xDDDDDD);
			drawString(poseStack, font, Component.translatable("option.orespawn.host_tags"),
					contentLeft, 110, 0xDDDDDD);
		}
		if (error != null) {
			drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font, error, contentWidth),
					width / 2, compact ? 23 : height - 40, 0xFF5555);
		}
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void drawCompactPlacementLabels(PoseStack poseStack) {
		String[][] labels = {
				{ "min_y", "max_y" },
				{ "frequency", "discard_air_exposure" },
				{ "min_quantity", "max_quantity" }
		};
		for (int row = 0; row < labels.length; row++) {
			for (int column = 0; column < labels[row].length; column++) {
				Component label = Component.translatable("option.orespawn." + labels[row][column]);
				int x = column == 0 ? contentLeft : contentLeft + columnWidth + 5;
				drawString(poseStack, font, OreSpawnScreenLayout.fit(font, label, columnWidth),
						x, OreSpawnScreenLayout.compactOrePlacementLabelY(height, row), 0xDDDDDD);
			}
		}
	}

	private Component patternName(OrePattern value) {
		return Component.translatable("value.orespawn.ore_pattern." + value.configName);
	}

	private Component distributionName(OreHeightDistribution value) {
		return Component.translatable("value.orespawn.height_distribution." + value.configName);
	}

	private Component richnessName(OreRichnessPreset value) {
		return Component.translatable("value.orespawn.ore_richness." + value.configName);
	}

	private net.minecraft.client.gui.components.Tooltip tooltip(String key) {
		return net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key));
	}
}
