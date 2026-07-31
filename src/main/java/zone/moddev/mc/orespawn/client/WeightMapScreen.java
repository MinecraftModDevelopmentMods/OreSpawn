package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

final class WeightMapScreen extends Screen {
	private static final int PAGE_SIZE = 7;
	private final Screen parent;
	private final JsonObject weights;
	private final List<String> keys;
	private final double defaultWeight;
	private final String tooltipKey;
	private final Runnable removeAction;
	private final List<EditBox> editors = new ArrayList<>();
	private int page;
	private Component error;

	WeightMapScreen(Screen parent, Component title, JsonObject weights, List<String> keys, double defaultWeight) {
		this(parent, title, weights, keys, defaultWeight, "tooltip.orespawn.geome.entry_weight", null);
	}

	WeightMapScreen(Screen parent, Component title, JsonObject weights, List<String> keys, double defaultWeight,
			Runnable removeAction) {
		this(parent, title, weights, keys, defaultWeight, "tooltip.orespawn.geome.biome_weight", removeAction);
	}

	private WeightMapScreen(Screen parent, Component title, JsonObject weights, List<String> keys,
			double defaultWeight, String tooltipKey, Runnable removeAction) {
		super(title);
		this.parent = parent;
		this.weights = weights;
		this.keys = new ArrayList<>(keys);
		this.defaultWeight = defaultWeight;
		this.tooltipKey = tooltipKey;
		this.removeAction = removeAction;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		editors.clear();
		int start = page * PAGE_SIZE;
		int end = Math.min(keys.size(), start + PAGE_SIZE);
		for (int i = start; i < end; i++) {
			String key = keys.get(i);
			EditBox box = new EditBox(font, width / 2 + 5, 38 + ((i - start) * 24), 110, 20,
					new TextComponent(key));
			box.setMaxLength(24);
			box.setValue(weights.has(key) ? weights.get(key).getAsString() : Double.toString(defaultWeight));
			editors.add(OreSpawnScreenLayout.explain(this, addRenderableWidget(box), tooltipKey));
		}
		int bottom = height - 28;
		addRenderableWidget(new Button(width / 2 - 155, bottom, 100, 20, CommonComponents.GUI_DONE,
				button -> saveAndClose()));
		addRenderableWidget(new Button(width / 2 + 55, bottom, 100, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
		Button previous = addRenderableWidget(new Button(width / 2 - 50, bottom, 45, 20,
				new TextComponent("<"), button -> changePage(-1)));
		Button next = addRenderableWidget(new Button(width / 2 + 5, bottom, 45, 20,
				new TextComponent(">"), button -> changePage(1)));
		previous.active = page > 0;
		next.active = (page + 1) * PAGE_SIZE < keys.size();
		if (removeAction != null) {
			addRenderableWidget(new Button(width - 105, 8, 95, 20,
					new TextComponent("Remove rule"), button -> {
						removeAction.run(); minecraft.setScreen(parent);
					}));
		}
	}

	private void changePage(int offset) {
		if (savePage()) {
			page += offset;
			rebuildWidgets();
		}
	}

	private void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private void saveAndClose() {
		if (savePage()) minecraft.setScreen(parent);
	}

	private boolean savePage() {
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			try {
				double value = Double.parseDouble(editors.get(i).getValue().trim());
				if (!Double.isFinite(value) || value < 0.0D || value > 1000.0D) {
					throw new NumberFormatException();
				}
				weights.addProperty(keys.get(start + i), value);
			} catch (NumberFormatException e) {
				error = new TextComponent("Weights must be between 0 and 1000.");
				return false;
			}
		}
		error = null;
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 14, 0xFFFFFF);
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			drawString(poseStack, font, keys.get(start + i), width / 2 - 155, 44 + (i * 24), 0xDDDDDD);
		}
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, height - 42, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
