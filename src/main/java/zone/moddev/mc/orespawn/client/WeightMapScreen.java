package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class WeightMapScreen extends OreSpawnScreen {
	private static final int PAGE_SIZE = 7;
	private final Screen parent;
	private final JsonObject weights;
	private final List<String> keys;
	private final double defaultWeight;
	private final Runnable removeAction;
	private final List<EditBox> editors = new ArrayList<>();
	private int page;
	private Component error;

	WeightMapScreen(Screen parent, Component title, JsonObject weights, List<String> keys, double defaultWeight) {
		this(parent, title, weights, keys, defaultWeight, null);
	}

	WeightMapScreen(Screen parent, Component title, JsonObject weights, List<String> keys, double defaultWeight,
			Runnable removeAction) {
		super(title);
		this.parent = parent;
		this.weights = weights;
		this.keys = new ArrayList<>(keys);
		this.defaultWeight = defaultWeight;
		this.removeAction = removeAction;
	}

	@Override
	protected void init() {
		editors.clear();
		int start = page * PAGE_SIZE;
		int end = Math.min(keys.size(), start + PAGE_SIZE);
		for (int i = start; i < end; i++) {
			String key = keys.get(i);
			EditBox box = new EditBox(font, width / 2 + 5, 38 + ((i - start) * 24), 110, 20,
					Component.literal(key));
			box.setMaxLength(24);
			box.setValue(weights.has(key) ? weights.get(key).getAsString() : Double.toString(defaultWeight));
			editors.add(addRenderableWidget(box));
		}
		int bottom = height - 28;
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 155, bottom, 100, 20, CommonComponents.GUI_DONE,
				button -> saveAndClose()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 + 55, bottom, 100, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 50, bottom, 45, 20,
				Component.literal("<"), button -> changePage(-1)));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 + 5, bottom, 45, 20,
				Component.literal(">"), button -> changePage(1)));
		previous.active = page > 0;
		next.active = (page + 1) * PAGE_SIZE < keys.size();
		if (removeAction != null) {
			addRenderableWidget(OreSpawnScreenLayout.plainButton(width - 105, 8, 95, 20,
					Component.literal("Remove rule"), button -> {
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

	protected void rebuildWidgets() {
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
				error = Component.literal("Weights must be between 0 and 1000.");
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
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawCenteredString(font, title, width / 2, 14, OreSpawnScreenLayout.TEXT_PRIMARY);
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			graphics.drawString(font, keys.get(start + i), width / 2 - 155, 44 + (i * 24), OreSpawnScreenLayout.TEXT_SECONDARY);
		}
		if (error != null) graphics.drawCenteredString(font, error, width / 2, height - 42, OreSpawnScreenLayout.TEXT_ERROR);
	}
}
