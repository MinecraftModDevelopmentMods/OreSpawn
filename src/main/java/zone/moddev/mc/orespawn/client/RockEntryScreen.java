package zone.moddev.mc.orespawn.client;

import java.util.Arrays;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

final class RockEntryScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String blockId;
	private RockFamily family;
	private boolean enabled;
	private boolean oreReplaceable;
	private TextFieldWidget weight;
	private TextFieldWidget peak;
	private TextFieldWidget spread;
	private TextFieldWidget minY;
	private TextFieldWidget maxY;
	private ITextComponent error;

	RockEntryScreen(Screen parent, GeologyEditorSession session, String blockId) {
		super(new TranslationTextComponent("screen.orespawn.rock_entry"));
		this.parent = parent;
		this.session = session;
		this.blockId = blockId;
		load();
	}

	private void load() {
		JsonObject rock = session.rock(blockId);
		try {
			family = RockFamily.fromConfigName(GeologyEditorSession.string(rock, "family", "sedimentary"));
		} catch (RuntimeException e) {
			family = RockFamily.SEDIMENTARY;
		}
		enabled = GeologyEditorSession.bool(rock, "enabled", true);
		oreReplaceable = GeologyEditorSession.bool(rock, "ore_replaceable", true);
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		JsonObject rock = session.rock(blockId);
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::familyName)
				.withValues(Arrays.asList(RockFamily.values())).withInitialValue(family)
				.create(left, 38, 190, 20, new TranslationTextComponent("option.orespawn.family"),
						(button, value) -> family = value)), "tooltip.orespawn.rock.family");
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.onOffBuilder(enabled)
				.create(left + 200, 38, 110, 20, new TranslationTextComponent("option.orespawn.enabled"),
						(button, value) -> enabled = value)), "tooltip.orespawn.enabled");
		weight = addField(right, 64, "weight", value(rock, "weight", 1.0D));
		peak = addField(right, 86, "depth_peak", value(rock, "depth_peak", 48));
		spread = addField(right, 108, "depth_spread", value(rock, "depth_spread", 40));
		minY = addField(right, 130, "min_y", value(rock, "min_y", 0));
		maxY = addField(right, 152, "max_y", value(rock, "max_y", 255));
		addButton(OreSpawnScreenLayout.explain(this,
				CycleButton.onOffBuilder(oreReplaceable)
						.create(left, 176, 150, 20,
								new TranslationTextComponent("option.orespawn.ore_replaceable"),
								(button, value) -> oreReplaceable = value),
				"tooltip.orespawn.rock.ore_replaceable"));
		addButton(OreSpawnScreenLayout.explain(this,
				new Button(right, 176, 150, 20,
						new TranslationTextComponent("button.orespawn.geome_weights"),
						button -> openWeights()),
				"tooltip.orespawn.geome_weights"));
		int bottom = height - 28;
		addButton(new Button(left, bottom, 95, 20, DialogTexts.GUI_DONE, button -> saveAndClose()));
		addButton(new Button(left + 100, bottom, 95, 20,
				new TranslationTextComponent("button.orespawn.reset"), button -> reset()));
		addButton(new Button(right + 45, bottom, 105, 20,
				new TranslationTextComponent("button.orespawn.remove"), button -> remove()));
	}

	private TextFieldWidget addField(int x, int y, String key, String initial) {
		TextFieldWidget box = new TextFieldWidget(font, x, y, 150, 20, new StringTextComponent(key));
		box.setMaxLength(32);
		box.setValue(initial);
		OreSpawnScreenLayout.explain(this, box, rockFieldHelp(key));
		return addButton(box);
	}

	private static String rockFieldHelp(String key) {
		return "weight".equals(key) ? "tooltip.orespawn.weight"
				: "tooltip.orespawn.rock." + key;
	}

	private void openWeights() {
		if (!save()) return;
		JsonObject rock = session.rock(blockId);
		JsonObject weights = rock.has("geomes") && rock.get("geomes").isJsonObject()
				? rock.getAsJsonObject("geomes") : new JsonObject();
		rock.add("geomes", weights);
		minecraft.setScreen(new WeightMapScreen(this, new TranslationTextComponent("screen.orespawn.geome_weights"),
				weights, session.geomeIds(), 1.0D));
	}

	private void saveAndClose() {
		if (save()) minecraft.setScreen(parent);
	}

	private boolean save() {
		try {
			double parsedWeight = number(weight, 0.0D, 1000.0D);
			int parsedPeak = integer(peak, 0, 255);
			int parsedSpread = integer(spread, 1, 512);
			int parsedMin = integer(minY, 0, 255);
			int parsedMax = integer(maxY, 0, 255);
			if (parsedMin > parsedMax) throw new NumberFormatException();
			JsonObject rock = session.rock(blockId);
			rock.addProperty("enabled", enabled);
			rock.addProperty("family", family.configName);
			rock.addProperty("weight", parsedWeight);
			rock.addProperty("depth_peak", parsedPeak);
			rock.addProperty("depth_spread", parsedSpread);
			rock.addProperty("min_y", parsedMin);
			rock.addProperty("max_y", parsedMax);
			rock.addProperty("ore_replaceable", oreReplaceable);
			error = null;
			return true;
		} catch (NumberFormatException e) {
			error = new StringTextComponent("Check the numeric values and Y range.");
			return false;
		}
	}

	private void reset() {
		session.resetEntry("rocks", blockId);
		load();
		rebuildWidgets();
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	private void remove() {
		session.removeRock(blockId);
		minecraft.setScreen(parent);
	}

	private double number(TextFieldWidget box, double min, double max) {
		double value = Double.parseDouble(box.getValue().trim());
		if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
		return value;
	}

	private int integer(TextFieldWidget box, int min, int max) {
		double value = number(box, min, max);
		if (value != Math.rint(value)) throw new NumberFormatException();
		return (int) value;
	}

	private String value(JsonObject json, String key, Number fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback.toString();
	}

	private ITextComponent familyName(RockFamily value) {
		return new TranslationTextComponent("value.orespawn.family." + value.configName);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 6, 0xFFFFFF);
		drawCenteredString(poseStack, font,
				new StringTextComponent(session.materialBlockId(GeologyEditorSession.MaterialTab.SEDIMENTARY, blockId)),
				width / 2, 20, 0xDDDDDD);
		String[] labels = { "weight", "depth_peak", "depth_spread", "min_y", "max_y" };
		for (int i = 0; i < labels.length; i++) {
			drawString(poseStack, font, new TranslationTextComponent("option.orespawn." + labels[i]),
					width / 2 - 155, 70 + (i * 22), 0xDDDDDD);
		}
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, height - 42, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
