package zone.moddev.mc.orespawn.client;

import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

final class GeomeEntryScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String geomeId;
	private final Map<RockFamily, TextFieldWidget> familyWeights = new EnumMap<>(RockFamily.class);
	private TextFieldWidget baseWeight;
	private ITextComponent error;

	GeomeEntryScreen(Screen parent, GeologyEditorSession session, String geomeId) {
		super(new TranslationTextComponent("screen.orespawn.geome_entry"));
		this.parent = parent;
		this.session = session;
		this.geomeId = geomeId;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		familyWeights.clear();
		JsonObject geome = session.weightMap("geomes", geomeId);
		JsonObject families = object(geome, "families");
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		baseWeight = addField(right, 52, "base", text(geome, "base", 1.0D),
				"tooltip.orespawn.geome.base_weight");
		int index = 0;
		for (RockFamily family : RockFamily.values()) {
			TextFieldWidget field = addField(right, 82 + (index * 25), family.configName,
					text(families, family.configName, 1.0D), "tooltip.orespawn.geome.family_weight");
			familyWeights.put(family, field);
			index++;
		}
		addButton(new Button(left, 190, 150, 20,
				new TranslationTextComponent("button.orespawn.reset"), button -> reset()));
		Button remove = addButton(new Button(right, 190, 150, 20,
				new TranslationTextComponent("button.orespawn.remove"), button -> remove()));
		remove.active = !GeologyEditorSession.BUILT_IN_GEOMES.contains(geomeId);
		int bottom = height - 28;
		addButton(new Button(left, bottom, 150, 20, DialogTexts.GUI_DONE,
				button -> saveAndClose()));
		addButton(new Button(right, bottom, 150, 20, DialogTexts.GUI_CANCEL,
				button -> onClose()));
	}

	private TextFieldWidget addField(int x, int y, String key, String value, String tooltipKey) {
		TextFieldWidget field = new TextFieldWidget(font, x, y, 150, 20, new StringTextComponent(key));
		field.setMaxLength(32);
		field.setValue(value);
		return OreSpawnScreenLayout.explain(this, addButton(field), tooltipKey);
	}

	private void saveAndClose() {
		try {
			JsonObject geome = session.weightMap("geomes", geomeId);
			geome.addProperty("base", weight(baseWeight));
			JsonObject families = object(geome, "families");
			for (RockFamily family : RockFamily.values()) {
				families.addProperty(family.configName, weight(familyWeights.get(family)));
			}
			error = null;
			minecraft.setScreen(parent);
		} catch (NumberFormatException e) {
			error = new StringTextComponent("Weights must be between 0 and 1000.");
		}
	}

	private double weight(TextFieldWidget field) {
		double value = Double.parseDouble(field.getValue().trim());
		if (!Double.isFinite(value) || value < 0.0D || value > 1000.0D) throw new NumberFormatException();
		return value;
	}

	private void reset() {
		session.resetEntry("geomes", geomeId);
		rebuildWidgets();
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	private void remove() {
		session.removeGeome(geomeId);
		minecraft.setScreen(parent);
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (!parent.has(key) || !parent.get(key).isJsonObject()) {
			JsonObject result = new JsonObject();
			parent.add(key, result);
			return result;
		}
		return parent.getAsJsonObject(key);
	}

	private static String text(JsonObject json, String key, Number fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback.toString();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(font, new StringTextComponent(geomeId), width / 2, 30, 0xDDDDDD);
		drawString(font, new TranslationTextComponent("option.orespawn.base_weight"),
				width / 2 - 155, 58, 0xDDDDDD);
		int index = 0;
		for (RockFamily family : RockFamily.values()) {
			drawString(font, new TranslationTextComponent("value.orespawn.family." + family.configName),
					width / 2 - 155, 88 + (index * 25), 0xDDDDDD);
			index++;
		}
		if (error != null) drawCenteredString(font, error, width / 2, height - 42, 0xFF5555);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
