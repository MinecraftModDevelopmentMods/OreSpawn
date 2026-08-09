package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

final class WeightMapScreen extends OreSpawnScreen {
	private static final int PAGE_SIZE = 7;
	private final Screen parent;
	private final JsonObject weights;
	private final List<String> keys;
	private final double defaultWeight;
	private final String tooltipKey;
	private final Runnable removeAction;
	private final List<TextFieldWidget> editors = new ArrayList<>();
	private int page;
	private ITextComponent error;

	WeightMapScreen(Screen parent, ITextComponent title, JsonObject weights, List<String> keys, double defaultWeight) {
		this(parent, title, weights, keys, defaultWeight, "tooltip.orespawn.geome.entry_weight", null);
	}

	WeightMapScreen(Screen parent, ITextComponent title, JsonObject weights, List<String> keys, double defaultWeight,
			Runnable removeAction) {
		this(parent, title, weights, keys, defaultWeight, "tooltip.orespawn.geome.biome_weight", removeAction);
	}

	private WeightMapScreen(Screen parent, ITextComponent title, JsonObject weights, List<String> keys,
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
			TextFieldWidget box = new TextFieldWidget(font, width / 2 + 5, 38 + ((i - start) * 24), 110, 20,
					new StringTextComponent(key));
			box.setMaxLength(24);
			box.setValue(weights.has(key) ? weights.get(key).getAsString() : Double.toString(defaultWeight));
			editors.add(OreSpawnScreenLayout.explain(this, addButton(box), tooltipKey));
		}
		int bottom = height - 28;
		addButton(new Button(width / 2 - 155, bottom, 100, 20, DialogTexts.GUI_DONE,
				button -> saveAndClose()));
		addButton(new Button(width / 2 + 55, bottom, 100, 20, DialogTexts.GUI_CANCEL,
				button -> onClose()));
		Button previous = addButton(new Button(width / 2 - 50, bottom, 45, 20,
				new StringTextComponent("<"), button -> changePage(-1)));
		Button next = addButton(new Button(width / 2 + 5, bottom, 45, 20,
				new StringTextComponent(">"), button -> changePage(1)));
		previous.active = page > 0;
		next.active = (page + 1) * PAGE_SIZE < keys.size();
		if (removeAction != null) {
			addButton(new Button(width - 105, 8, 95, 20,
					new StringTextComponent("Remove rule"), button -> {
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
		buttons.clear(); children.clear();
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
				error = new StringTextComponent("Weights must be between 0 and 1000.");
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
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			drawString(font, keys.get(start + i), width / 2 - 155, 44 + (i * 24), 0xDDDDDD);
		}
		if (error != null) drawCenteredString(font, error, width / 2, height - 42, 0xFF5555);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
