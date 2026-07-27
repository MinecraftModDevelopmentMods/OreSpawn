package zone.moddev.mc.orespawn.client;

import java.util.EnumMap;
import java.util.Map;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

final class GeomeEntryScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String geomeId;
	private final Map<RockFamily, EditBox> familyWeights = new EnumMap<>(RockFamily.class);
	private EditBox baseWeight;
	private Component error;

	GeomeEntryScreen(Screen parent, GeologyEditorSession session, String geomeId) {
		super(new TranslatableComponent("screen.orespawn.geome_entry"));
		this.parent = parent;
		this.session = session;
		this.geomeId = geomeId;
	}

	@Override
	protected void init() {
		familyWeights.clear();
		JsonObject geome = session.weightMap("geomes", geomeId);
		JsonObject families = object(geome, "families");
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		baseWeight = addField(right, 52, "base", text(geome, "base", 1.0D));
		int index = 0;
		for (RockFamily family : RockFamily.values()) {
			EditBox field = addField(right, 82 + (index * 25), family.configName,
					text(families, family.configName, 1.0D));
			familyWeights.put(family, field);
			index++;
		}
		addRenderableWidget(new Button(left, 190, 150, 20,
				new TranslatableComponent("button.orespawn.reset"), button -> reset()));
		Button remove = addRenderableWidget(new Button(right, 190, 150, 20,
				new TranslatableComponent("button.orespawn.remove"), button -> remove()));
		remove.active = !GeologyEditorSession.BUILT_IN_GEOMES.contains(geomeId);
		int bottom = height - 28;
		addRenderableWidget(new Button(left, bottom, 150, 20, CommonComponents.GUI_DONE,
				button -> saveAndClose()));
		addRenderableWidget(new Button(right, bottom, 150, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
	}

	private EditBox addField(int x, int y, String key, String value) {
		EditBox field = new EditBox(font, x, y, 150, 20, new TextComponent(key));
		field.setMaxLength(32);
		field.setValue(value);
		return addRenderableWidget(field);
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
			error = new TextComponent("Weights must be between 0 and 1000.");
		}
	}

	private double weight(EditBox field) {
		double value = Double.parseDouble(field.getValue().trim());
		if (!Double.isFinite(value) || value < 0.0D || value > 1000.0D) throw new NumberFormatException();
		return value;
	}

	private void reset() {
		session.resetEntry("geomes", geomeId);
		rebuildWidgets();
	}

	private void rebuildWidgets() {
		clearWidgets();
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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(poseStack, font, new TextComponent(geomeId), width / 2, 30, 0xDDDDDD);
		drawString(poseStack, font, new TranslatableComponent("option.orespawn.base_weight"),
				width / 2 - 155, 58, 0xDDDDDD);
		int index = 0;
		for (RockFamily family : RockFamily.values()) {
			drawString(poseStack, font, new TranslatableComponent("value.orespawn.family." + family.configName),
					width / 2 - 155, 88 + (index * 25), 0xDDDDDD);
			index++;
		}
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, height - 42, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
