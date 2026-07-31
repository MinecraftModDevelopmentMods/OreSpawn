package zone.moddev.mc.orespawn.client;

import java.util.Arrays;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class RockEntryScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String blockId;
	private RockFamily family;
	private boolean enabled;
	private boolean oreReplaceable;
	private EditBox weight;
	private EditBox peak;
	private EditBox spread;
	private EditBox minY;
	private EditBox maxY;
	private Component error;

	RockEntryScreen(Screen parent, GeologyEditorSession session, String blockId) {
		super(Component.translatable("screen.orespawn.rock_entry"));
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
		JsonObject rock = session.rock(blockId);
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		addRenderableWidget(CycleButton.builder(this::familyName, family)
				.withValues(Arrays.asList(RockFamily.values()))
				.withTooltip(value -> net.minecraft.client.gui.components.Tooltip.create(
						Component.translatable("guide.orespawn.rocks.1")))
				.create(left, 38, 190, 20, Component.translatable("option.orespawn.family"),
						(button, value) -> family = value));
		addRenderableWidget(CycleButton.onOffBuilder(enabled)
				.create(left + 200, 38, 110, 20, Component.translatable("option.orespawn.enabled"),
						(button, value) -> enabled = value));
		weight = addField(right, 64, "weight", value(rock, "weight", 1.0D));
		peak = addField(right, 86, "depth_peak", value(rock, "depth_peak", 48));
		spread = addField(right, 108, "depth_spread", value(rock, "depth_spread", 40));
		minY = addField(right, 130, "min_y", value(rock, "min_y", -64));
		maxY = addField(right, 152, "max_y", value(rock, "max_y", 319));
		addRenderableWidget(OreSpawnScreenLayout.explain(
				CycleButton.onOffBuilder(oreReplaceable).create(left, 176, 150, 20,
						Component.translatable("option.orespawn.ore_replaceable"),
						(button, value) -> oreReplaceable = value),
				"guide.orespawn.rocks.3"));
		addRenderableWidget(OreSpawnScreenLayout.explain(
				OreSpawnScreenLayout.plainButton(right, 176, 150, 20,
						Component.translatable("button.orespawn.geome_weights"),
						button -> openWeights()),
				"guide.orespawn.rocks.1"));
		int bottom = height - 28;
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, bottom, 95, 20, CommonComponents.GUI_DONE, button -> saveAndClose()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 100, bottom, 95, 20,
				Component.translatable("button.orespawn.reset"), button -> reset()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(right + 45, bottom, 105, 20,
				Component.translatable("button.orespawn.remove"), button -> remove()));
	}

	private EditBox addField(int x, int y, String key, String initial) {
		EditBox box = new EditBox(font, x, y, 150, 20, Component.literal(key));
		box.setMaxLength(32);
		box.setValue(initial);
		OreSpawnScreenLayout.explain(box, "guide.orespawn.rocks.2");
		return addRenderableWidget(box);
	}

	private void openWeights() {
		if (!save()) return;
		JsonObject rock = session.rock(blockId);
		JsonObject weights = rock.has("geomes") && rock.get("geomes").isJsonObject()
				? rock.getAsJsonObject("geomes") : new JsonObject();
		rock.add("geomes", weights);
		minecraft.setScreen(new WeightMapScreen(this, Component.translatable("screen.orespawn.geome_weights"),
				weights, session.geomeIds(), 1.0D));
	}

	private void saveAndClose() {
		if (save()) minecraft.setScreen(parent);
	}

	private boolean save() {
		try {
			double parsedWeight = number(weight, 0.0D, 1000.0D);
			int parsedPeak = integer(peak, -64, 319);
			int parsedSpread = integer(spread, 1, 512);
			int parsedMin = integer(minY, -64, 319);
			int parsedMax = integer(maxY, -64, 319);
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
			error = Component.literal("Check the numeric values and Y range.");
			return false;
		}
	}

	private void reset() {
		session.resetEntry("rocks", blockId);
		load();
		rebuildWidgets();
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private void remove() {
		session.removeRock(blockId);
		minecraft.setScreen(parent);
	}

	private double number(EditBox box, double min, double max) {
		double value = Double.parseDouble(box.getValue().trim());
		if (!Double.isFinite(value) || value < min || value > max) throw new NumberFormatException();
		return value;
	}

	private int integer(EditBox box, int min, int max) {
		double value = number(box, min, max);
		if (value != Math.rint(value)) throw new NumberFormatException();
		return (int) value;
	}

	private String value(JsonObject json, String key, Number fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback.toString();
	}

	private Component familyName(RockFamily value) {
		return Component.translatable("value.orespawn.family." + value.configName);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawCenteredString(font, title, width / 2, 6, OreSpawnScreenLayout.TEXT_PRIMARY);
		graphics.drawCenteredString(font,
				Component.literal(session.materialBlockId(GeologyEditorSession.MaterialTab.SEDIMENTARY, blockId)),
				width / 2, 20, OreSpawnScreenLayout.TEXT_SECONDARY);
		String[] labels = { "weight", "depth_peak", "depth_spread", "min_y", "max_y" };
		for (int i = 0; i < labels.length; i++) {
			graphics.drawString(font, Component.translatable("option.orespawn." + labels[i]),
					width / 2 - 155, 70 + (i * 22), OreSpawnScreenLayout.TEXT_SECONDARY);
		}
		if (error != null) graphics.drawCenteredString(font, error, width / 2, height - 42, OreSpawnScreenLayout.TEXT_ERROR);
	}
}
