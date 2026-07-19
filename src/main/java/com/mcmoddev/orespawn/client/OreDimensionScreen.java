package com.mcmoddev.orespawn.client;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.worldgen.OreHeightDistribution;
import com.mcmoddev.orespawn.worldgen.OrePattern;
import com.mcmoddev.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class OreDimensionScreen extends Screen {
	private enum Page { PLACEMENT, PATTERN, HOSTS }

	private final Screen parent;
	private final GeologyEditorSession session;
	private final String oreId;
	private final String dimensionId;
	private final double baselineFrequency;
	private final EnumSet<RockFamily> families = EnumSet.noneOf(RockFamily.class);
	private boolean enabled;
	private EditBox minY;
	private EditBox maxY;
	private EditBox frequency;
	private EditBox quantity;
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

	OreDimensionScreen(Screen parent, GeologyEditorSession session, String oreId, String dimensionId) {
		super(new TranslatableComponent("screen.orespawn.ore_dimension"));
		this.parent = parent;
		this.session = session;
		this.oreId = oreId;
		this.dimensionId = dimensionId;
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
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		pageButtons.clear();
		placementWidgets.clear();
		patternWidgets.clear();
		hostWidgets.clear();
		addRenderableWidget(CycleButton.onOffBuilder(enabled).create(left, 32, 310, 20,
				new TranslatableComponent("option.orespawn.enabled"), (button, value) -> enabled = value));
		pageButtons.add(addRenderableWidget(new Button(left, 56, 100, 20,
				new TranslatableComponent("tab.orespawn.placement"), button -> showPage(Page.PLACEMENT))));
		pageButtons.add(addRenderableWidget(new Button(left + 105, 56, 100, 20,
				new TranslatableComponent("tab.orespawn.pattern"), button -> showPage(Page.PATTERN))));
		pageButtons.add(addRenderableWidget(new Button(left + 210, 56, 100, 20,
				new TranslatableComponent("tab.orespawn.hosts"), button -> showPage(Page.HOSTS))));
		double currentFrequency = GeologyEditorSession.decimal(rule, "frequency", baselineFrequency);
		richness = addRenderableWidget(CycleButton.builder(this::richnessName)
				.withValues(Arrays.asList(OreRichnessPreset.values()))
				.withInitialValue(OreRichnessPreset.fromFrequency(baselineFrequency, currentFrequency))
				.withTooltip(value -> tooltip("tooltip.orespawn.ore_richness"))
				.create(left, 80, 310, 20, new TranslatableComponent("option.orespawn.ore_richness"),
						(button, value) -> applyRichness(value)));
		placementWidgets.add(richness);
		minY = addPlacementField(right, 104, "min_y", text(rule, "min_y", -64));
		maxY = addPlacementField(right, 128, "max_y", text(rule, "max_y", 64));
		frequency = addPlacementField(right, 152, "frequency", text(rule, "frequency", 1.0D));
		quantity = addPlacementField(right, 176, "quantity", text(rule, "quantity", 8));

		AbstractWidget patternButton;
		if (externalPattern) {
			Button external = new Button(left, 80, 310, 20,
					new TextComponent("Pattern: " + externalPatternId), button -> { });
			external.active = false;
			patternButton = addRenderableWidget(external);
		} else {
			patternButton = addRenderableWidget(CycleButton.builder(this::patternName)
					.withValues(Arrays.asList(OrePattern.values()))
					.withInitialValue(pattern)
					.create(left, 80, 310, 20, new TranslatableComponent("option.orespawn.pattern"),
							(button, value) -> {
								pattern = value;
								updatePatternControls();
							}));
		}
		patternWidgets.add(patternButton);
		patternWidgets.add(addRenderableWidget(CycleButton.builder(this::distributionName)
				.withValues(Arrays.asList(OreHeightDistribution.values()))
				.withInitialValue(heightDistribution)
				.create(left, 104, 310, 20,
						new TranslatableComponent("option.orespawn.height_distribution"),
						(button, value) -> heightDistribution = value)));
		spread = addPatternField(right, 128, "spread", text(rule, "spread", 8));
		verticalSpread = addPatternField(right, 152, "vertical_spread", text(rule, "vertical_spread", 4));
		nodeSize = addPatternField(right, 176, "node_size", text(rule, "node_size", 4));

		originalHostBlocksText = join(rule.get("host_blocks"), "block");
		originalHostTagsText = join(rule.get("host_tags"), "tag");
		hostBlocks = addHostField(left, 88, "host_blocks", originalHostBlocksText);
		hostTags = addHostField(left, 120, "host_tags", originalHostTagsText);
		Button weights = addRenderableWidget(new Button(left, 144, 150, 20,
				new TranslatableComponent("button.orespawn.geome_weights"), button -> openWeights()));
		weights.active = "minecraft:overworld".equals(dimensionId);
		hostWidgets.add(weights);
		hostWidgets.add(addRenderableWidget(new Button(right, 144, 150, 20,
				new TranslatableComponent("button.orespawn.remove_dimension"), button -> removeDimension())));

		RockFamily[] values = RockFamily.values();
		for (int i = 0; i < values.length; i++) {
			RockFamily family = values[i];
			int x = (i & 1) == 0 ? left : right;
			int y = 168 + ((i / 2) * 22);
			hostWidgets.add(addRenderableWidget(CycleButton.onOffBuilder(families.contains(family)).create(x, y, 150, 20,
					new TranslatableComponent("value.orespawn.family." + family.configName),
					(button, selected) -> {
						if (selected) families.add(family); else families.remove(family);
					})));
		}

		int bottom = height - 28;
		addRenderableWidget(new Button(left, bottom, 150, 20, CommonComponents.GUI_DONE,
				button -> saveAndClose()));
		addRenderableWidget(new Button(right, bottom, 150, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
		showPage(page);
		updatePatternControls();
	}

	private EditBox addPlacementField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, 150, 20, new TextComponent(key));
		box.setValue(value);
		box.setMaxLength(32);
		placementWidgets.add(addRenderableWidget(box));
		return box;
	}

	private EditBox addHostField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, 310, 20, new TextComponent(key));
		box.setValue(value);
		box.setMaxLength(1024);
		hostWidgets.add(addRenderableWidget(box));
		return box;
	}

	private EditBox addPatternField(int x, int y, String key, String value) {
		EditBox box = new EditBox(font, x, y, 150, 20, new TextComponent(key));
		box.setValue(value);
		box.setMaxLength(32);
		patternWidgets.add(addRenderableWidget(box));
		return box;
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
		minecraft.setScreen(new WeightMapScreen(this, new TranslatableComponent("screen.orespawn.geome_weights"),
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
			int parsedQuantity = integer(quantity, 1, 64);
			int parsedSpread = integer(spread, 0, 64);
			int parsedVerticalSpread = integer(verticalSpread, 0, 64);
			int parsedNodeSize = integer(nodeSize, 1, 32);
			if (parsedMin > parsedMax) throw new NumberFormatException();
			JsonArray blocks = ids(hostBlocks.getValue());
			JsonArray tags = ids(hostTags.getValue());
			if (enabled && families.isEmpty() && blocks.size() == 0 && tags.size() == 0) {
				error = new TextComponent("An enabled dimension needs at least one host.");
				return false;
			}
			JsonObject rule = rule();
			rule.addProperty("enabled", enabled);
			rule.addProperty("min_y", parsedMin);
			rule.addProperty("max_y", parsedMax);
			rule.addProperty("frequency", parsedFrequency);
			rule.addProperty("quantity", parsedQuantity);
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
			error = new TextComponent("Check the numeric ranges and registry IDs.");
			return false;
		}
	}

	private void removeDimension() {
		JsonObject ore = session.ore(oreId);
		ore.getAsJsonObject("dimensions").remove(dimensionId);
		minecraft.setScreen(parent);
	}

	private JsonObject rule() {
		JsonObject ore = session.ore(oreId);
		if (!ore.has("dimensions") || !ore.get("dimensions").isJsonObject()) {
			ore.add("dimensions", new JsonObject());
		}
		JsonObject dimensions = ore.getAsJsonObject("dimensions");
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
		drawCenteredString(poseStack, font, title, width / 2, 6, 0xFFFFFF);
		drawCenteredString(poseStack, font, new TextComponent(oreId + " / " + dimensionId),
				width / 2, 19, 0xDDDDDD);
		if (page == Page.PLACEMENT) {
			String[] labels = { "min_y", "max_y", "frequency", "quantity" };
			for (int i = 0; i < labels.length; i++) {
				drawString(poseStack, font, new TranslatableComponent("option.orespawn." + labels[i]),
						width / 2 - 155, 110 + (i * 24), 0xDDDDDD);
			}
		} else if (page == Page.PATTERN && !externalPattern) {
			String[] labels = { "spread", "vertical_spread", "node_size" };
			for (int i = 0; i < labels.length; i++) {
				drawString(poseStack, font, new TranslatableComponent("option.orespawn." + labels[i]),
						width / 2 - 155, 134 + (i * 24), 0xDDDDDD);
			}
		} else if (page == Page.PATTERN) {
			drawCenteredString(poseStack, font,
					new TranslatableComponent("message.orespawn.external_pattern_read_only"),
					width / 2, 132, 0xAAAAAA);
		} else {
			drawString(poseStack, font, new TranslatableComponent("option.orespawn.host_blocks"),
					width / 2 - 155, 78, 0xDDDDDD);
			drawString(poseStack, font, new TranslatableComponent("option.orespawn.host_tags"),
					width / 2 - 155, 110, 0xDDDDDD);
		}
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, height - 40, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private Component patternName(OrePattern value) {
		return new TranslatableComponent("value.orespawn.ore_pattern." + value.configName);
	}

	private Component distributionName(OreHeightDistribution value) {
		return new TranslatableComponent("value.orespawn.height_distribution." + value.configName);
	}

	private Component richnessName(OreRichnessPreset value) {
		return new TranslatableComponent("value.orespawn.ore_richness." + value.configName);
	}

	private List<FormattedCharSequence> tooltip(String key) {
		return font.split(new TranslatableComponent(key), 240);
	}
}
