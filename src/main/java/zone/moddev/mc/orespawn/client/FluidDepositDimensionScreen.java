package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;

final class FluidDepositDimensionScreen extends Screen {
	private enum Page { PLACEMENT, HOSTS, BIOMES }

	private final Screen parent;
	private final GeologyEditorSession session;
	private final String depositId;
	private final String dimensionId;
	private final EnumSet<RockFamily> families = EnumSet.noneOf(RockFamily.class);
	private final List<Button> pageButtons = new ArrayList<>();
	private final List<Widget> placementWidgets = new ArrayList<>();
	private final List<Widget> hostWidgets = new ArrayList<>();
	private final List<Widget> biomeWidgets = new ArrayList<>();
	private boolean enabled;
	private Page page = Page.PLACEMENT;
	private TextFieldWidget minY;
	private TextFieldWidget maxY;
	private TextFieldWidget frequency;
	private TextFieldWidget minRadius;
	private TextFieldWidget maxRadius;
	private TextFieldWidget minVertical;
	private TextFieldWidget maxVertical;
	private TextFieldWidget maxLobes;
	private TextFieldWidget minCover;
	private TextFieldWidget minShell;
	private TextFieldWidget hostBlocks;
	private TextFieldWidget hostTags;
	private TextFieldWidget biomeIds;
	private TextFieldWidget excludedBiomeIds;
	private TextFieldWidget biomeDictionary;
	private TextFieldWidget excludedBiomeDictionary;
	private ITextComponent error;
	private int left;
	private int contentWidth;
	private int columnWidth;

	FluidDepositDimensionScreen(Screen parent, GeologyEditorSession session,
			String depositId, String dimensionId) {
		super(new TranslationTextComponent("screen.orespawn.fluid_deposit_dimension"));
		this.parent = parent;
		this.session = session;
		this.depositId = depositId;
		this.dimensionId = dimensionId;
		load();
	}

	private void load() {
		JsonObject rule = rule();
		enabled = GeologyEditorSession.bool(rule, "enabled", true);
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
		OreSpawnScreenLayout.beginHelp(this);
		contentWidth = Math.min(390, Math.max(310, width - 24));
		left = (width - contentWidth) / 2;
		columnWidth = (contentWidth - 5) / 2;
		pageButtons.clear();
		placementWidgets.clear();
		hostWidgets.clear();
		biomeWidgets.clear();
		JsonObject rule = rule();
		int right = left + columnWidth + 5;
		OreSpawnScreenLayout.explain(this,
				addButton(CycleButton.onOffBuilder(enabled).create(left, 42, columnWidth, 20,
						new TranslationTextComponent("option.orespawn.enabled"),
						(button, value) -> enabled = value)),
				"tooltip.orespawn.enabled");
		addButton(OreSpawnScreenLayout.button(this, font, right, 42, columnWidth, 20,
				new TranslationTextComponent("button.orespawn.remove_dimension"), button -> removeDimension()));
		int tabWidth = (contentWidth - 10) / 3;
		pageButtons.add(addButton(OreSpawnScreenLayout.button(this, font, left, 66, tabWidth, 20,
				new TranslationTextComponent("tab.orespawn.placement"), button -> showPage(Page.PLACEMENT))));
		pageButtons.add(addButton(OreSpawnScreenLayout.button(this, font,
				left + tabWidth + 5, 66, tabWidth, 20,
				new TranslationTextComponent("tab.orespawn.hosts"), button -> showPage(Page.HOSTS))));
		pageButtons.add(addButton(OreSpawnScreenLayout.button(this, font,
				left + ((tabWidth + 5) * 2), 66, contentWidth - ((tabWidth + 5) * 2), 20,
				new TranslationTextComponent("tab.orespawn.biomes"), button -> showPage(Page.BIOMES))));

		minY = placementField(0, "min_y", text(rule, "min_y", 0));
		maxY = placementField(1, "max_y", text(rule, "max_y", 48));
		frequency = placementField(2, "frequency", text(rule, "frequency", 0.08D));
		minRadius = placementField(3, "min_radius", text(rule, "min_radius", 5));
		maxRadius = placementField(4, "max_radius", text(rule, "max_radius", 12));
		minVertical = placementField(5, "min_vertical_radius", text(rule, "min_vertical_radius", 2));
		maxVertical = placementField(6, "max_vertical_radius", text(rule, "max_vertical_radius", 5));
		maxLobes = placementField(7, "max_lobes", text(rule, "max_lobes", 4));
		minCover = placementField(8, "min_solid_cover", text(rule, "min_solid_cover", 2));
		minShell = placementField(9, "min_solid_shell", text(rule, "min_solid_shell", 1));

		hostBlocks = hostField(0, "host_blocks", join(rule.get("host_blocks"), "block"));
		hostTags = hostField(1, "host_tags", join(rule.get("host_tags"), "tag"));
		RockFamily[] values = RockFamily.values();
		for (int i = 0; i < values.length; i++) {
			RockFamily family = values[i];
			int x = (i & 1) == 0 ? left : right;
			int y = 136 + ((i / 2) * 24);
			hostWidgets.add(OreSpawnScreenLayout.explain(this,
					addButton(CycleButton.onOffBuilder(families.contains(family))
					.create(x, y, columnWidth, 20,
							new TranslationTextComponent("value.orespawn.family." + family.configName),
							(button, selected) -> {
								if (selected) families.add(family); else families.remove(family);
							})),
					"tooltip.orespawn.host_family"));
		}

		biomeIds = biomeField(0, "biome_ids", join(rule.get("biome_ids"), ""));
		excludedBiomeIds = biomeField(1, "excluded_biome_ids", join(rule.get("excluded_biome_ids"), ""));
		biomeDictionary = biomeField(2, "biome_dictionary", join(rule.get("biome_dictionary"), ""));
		excludedBiomeDictionary = biomeField(3, "excluded_biome_dictionary",
				join(rule.get("excluded_biome_dictionary"), ""));
		Button weights = addButton(OreSpawnScreenLayout.button(this, font, left, 180,
				contentWidth, 20, new TranslationTextComponent("button.orespawn.geome_weights"),
				button -> openWeights()));
		OreSpawnScreenLayout.explain(this, weights, "tooltip.orespawn.geome_weights");
		weights.active = "minecraft:overworld".equals(dimensionId) && !session.geomeIds().isEmpty();
		biomeWidgets.add(weights);

		addButton(OreSpawnScreenLayout.button(this, font, left, height - 28, columnWidth, 20,
				DialogTexts.GUI_DONE, button -> saveAndClose()));
		addButton(OreSpawnScreenLayout.button(this, font, right, height - 28, columnWidth, 20,
				DialogTexts.GUI_CANCEL, button -> onClose()));
		showPage(page);
	}

	private TextFieldWidget placementField(int index, String key, String value) {
		int groupX = index < 5 ? left : left + columnWidth + 5;
		int row = index < 5 ? index : index - 5;
		int fieldWidth = Math.min(72, Math.max(58, columnWidth / 3));
		TextFieldWidget box = new TextFieldWidget(font, groupX + columnWidth - fieldWidth, 90 + (row * 24),
				fieldWidth, 20, new StringTextComponent(key));
		box.setMaxLength(32); box.setValue(value);
		placementWidgets.add(OreSpawnScreenLayout.explain(this, addButton(box),
				placementHelp(key)));
		return box;
	}

	private TextFieldWidget hostField(int index, String key, String value) {
		int x = index == 0 ? left : left + columnWidth + 5;
		TextFieldWidget box = new TextFieldWidget(font, x, 106, columnWidth, 20, new StringTextComponent(key));
		box.setMaxLength(1024); box.setValue(value);
		hostWidgets.add(OreSpawnScreenLayout.explain(this, addButton(box),
				"tooltip.orespawn." + key));
		return box;
	}

	private TextFieldWidget biomeField(int index, String key, String value) {
		int x = (index & 1) == 0 ? left : left + columnWidth + 5;
		int y = 106 + ((index / 2) * 44);
		TextFieldWidget box = new TextFieldWidget(font, x, y, columnWidth, 20, new StringTextComponent(key));
		box.setMaxLength(1024); box.setValue(value);
		biomeWidgets.add(OreSpawnScreenLayout.explain(this, addButton(box),
				"tooltip.orespawn.fluid." + key));
		return box;
	}

	private static String placementHelp(String key) {
		return "tooltip.orespawn.fluid." + key;
	}

	private void showPage(Page selected) {
		page = selected;
		for (int i = 0; i < pageButtons.size(); i++) pageButtons.get(i).active = i != selected.ordinal();
		for (Widget widget : placementWidgets) widget.visible = selected == Page.PLACEMENT;
		for (Widget widget : hostWidgets) widget.visible = selected == Page.HOSTS;
		for (Widget widget : biomeWidgets) widget.visible = selected == Page.BIOMES;
	}

	private void openWeights() {
		if (!save()) return;
		JsonObject weights = rule().has("geomes") && rule().get("geomes").isJsonObject()
				? rule().getAsJsonObject("geomes") : new JsonObject();
		rule().add("geomes", weights);
		minecraft.setScreen(new WeightMapScreen(this,
				new TranslationTextComponent("screen.orespawn.geome_weights"),
				weights, session.geomeIds(), 1.0D));
	}

	private void saveAndClose() { if (save()) minecraft.setScreen(parent); }

	private boolean save() {
		try {
			int parsedMinY = integer(minY, 0, 255);
			int parsedMaxY = integer(maxY, 0, 255);
			double parsedFrequency = number(frequency, 0.0D, 64.0D);
			int parsedMinRadius = integer(minRadius, 1, 64);
			int parsedMaxRadius = integer(maxRadius, 1, 64);
			int parsedMinVertical = integer(minVertical, 1, 64);
			int parsedMaxVertical = integer(maxVertical, 1, 64);
			int parsedLobes = integer(maxLobes, 1, 16);
			int parsedCover = integer(minCover, 0, 64);
			int parsedShell = integer(minShell, 0, 64);
			if (parsedMinY > parsedMaxY || parsedMinRadius > parsedMaxRadius
					|| parsedMinVertical > parsedMaxVertical) throw new NumberFormatException();
			JsonArray blocks = ids(hostBlocks.getValue());
			JsonArray tags = ids(hostTags.getValue());
			if (enabled && blocks.size() == 0 && tags.size() == 0 && families.isEmpty()) {
				error = new TranslationTextComponent("error.orespawn.host_required");
				return false;
			}
			JsonObject rule = rule();
			rule.addProperty("enabled", enabled);
			rule.addProperty("min_y", parsedMinY);
			rule.addProperty("max_y", parsedMaxY);
			rule.addProperty("frequency", parsedFrequency);
			rule.addProperty("min_radius", parsedMinRadius);
			rule.addProperty("max_radius", parsedMaxRadius);
			rule.addProperty("min_vertical_radius", parsedMinVertical);
			rule.addProperty("max_vertical_radius", parsedMaxVertical);
			rule.addProperty("max_lobes", parsedLobes);
			rule.addProperty("min_solid_cover", parsedCover);
			rule.addProperty("min_solid_shell", parsedShell);
			JsonArray familyValues = new JsonArray();
			for (RockFamily family : families) familyValues.add(family.configName);
			rule.add("host_families", familyValues);
			rule.add("host_blocks", blocks);
			rule.add("host_tags", tags);
			rule.add("biome_ids", ids(biomeIds.getValue()));
			rule.add("excluded_biome_ids", ids(excludedBiomeIds.getValue()));
			rule.add("biome_dictionary", strings(biomeDictionary.getValue()));
			rule.add("excluded_biome_dictionary", strings(excludedBiomeDictionary.getValue()));
			error = null;
			return true;
		} catch (RuntimeException e) {
			error = new TranslationTextComponent("error.orespawn.invalid_values");
			return false;
		}
	}

	private void removeDimension() {
		dimensions().remove(dimensionId);
		minecraft.setScreen(parent);
	}

	private JsonObject rule() {
		JsonObject dimensions = dimensions();
		if (!dimensions.has(dimensionId) || !dimensions.get(dimensionId).isJsonObject()) {
			dimensions.add(dimensionId, new JsonObject());
		}
		return dimensions.getAsJsonObject(dimensionId);
	}

	private JsonObject dimensions() {
		JsonObject deposit = session.fluidDeposit(depositId);
		if (!deposit.has("dimensions") || !deposit.get("dimensions").isJsonObject()) {
			deposit.add("dimensions", new JsonObject());
		}
		return deposit.getAsJsonObject("dimensions");
	}

	private static String join(JsonElement element, String objectKey) {
		if (element == null || !element.isJsonArray()) return "";
		List<String> result = new ArrayList<>();
		for (JsonElement value : element.getAsJsonArray()) {
			if (value.isJsonObject() && !objectKey.isEmpty()) {
				result.add(GeologyEditorSession.string(value.getAsJsonObject(), objectKey, ""));
			} else result.add(value.getAsString());
		}
		return String.join(", ", result);
	}

	private static JsonArray ids(String text) {
		JsonArray result = new JsonArray();
		for (String value : split(text)) result.add(new ResourceLocation(value).toString());
		return result;
	}

	private static JsonArray strings(String text) {
		JsonArray result = new JsonArray();
		for (String value : split(text)) result.add(value);
		return result;
	}

	private static List<String> split(String text) {
		List<String> result = new ArrayList<>();
		Arrays.stream(text.split(",")).map(String::trim).filter(value -> !value.isEmpty())
				.forEach(result::add);
		return result;
	}

	private static int integer(TextFieldWidget field, int min, int max) {
		int value = Integer.parseInt(field.getValue().trim());
		if (value < min || value > max) throw new NumberFormatException();
		return value;
	}

	private static double number(TextFieldWidget field, double min, double max) {
		double value = Double.parseDouble(field.getValue().trim());
		if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
		return value;
	}

	private static String text(JsonObject json, String key, Number fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback.toString();
	}

	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 8, 0xFFFFFF);
		drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font,
				new StringTextComponent(depositId + " / " + dimensionId), Math.min(390, width - 24)),
				width / 2, 24, 0xAAAAAA);
		if (page == Page.PLACEMENT) {
			String[] keys = { "min_y", "max_y", "frequency", "min_radius", "max_radius",
					"min_vertical_radius", "max_vertical_radius", "max_lobes", "min_solid_cover",
					"min_solid_shell" };
			for (int i = 0; i < keys.length; i++) {
				int groupX = i < 5 ? left : left + columnWidth + 5;
				int row = i < 5 ? i : i - 5;
				int fieldWidth = Math.min(72, Math.max(58, columnWidth / 3));
				int labelWidth = columnWidth - fieldWidth - 5;
				drawString(poseStack, font, OreSpawnScreenLayout.fit(font,
						new TranslationTextComponent("option.orespawn." + keys[i]), labelWidth),
						groupX, 96 + (row * 24), 0xDDDDDD);
			}
		} else if (page == Page.HOSTS) {
			drawString(poseStack, font, OreSpawnScreenLayout.fit(font,
					new TranslationTextComponent("option.orespawn.host_blocks_short"), columnWidth),
					left, 94, 0xDDDDDD);
			drawString(poseStack, font, OreSpawnScreenLayout.fit(font,
					new TranslationTextComponent("option.orespawn.host_tags_short"), columnWidth),
					left + columnWidth + 5, 94, 0xDDDDDD);
		} else {
			String[] keys = { "biome_ids_short", "excluded_biome_ids_short",
					"biome_dictionary_short", "excluded_biome_dictionary_short" };
			for (int i = 0; i < keys.length; i++) {
				int x = (i & 1) == 0 ? left : left + columnWidth + 5;
				int y = 94 + ((i / 2) * 44);
				drawString(poseStack, font, OreSpawnScreenLayout.fit(font,
						new TranslationTextComponent("option.orespawn." + keys[i]), columnWidth),
						x, y, 0xDDDDDD);
			}
		}
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, height - 40, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
