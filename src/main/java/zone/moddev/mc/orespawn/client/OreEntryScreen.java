package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

final class OreEntryScreen extends OreSpawnScreen {
	private static final String BROAD_SELECTOR =
			OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString();
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String oreId;
	private boolean enabled;
	private int page;
	private EditBox dimensionId;
	private String dimensionText = "";

	OreEntryScreen(Screen parent, GeologyEditorSession session, String oreId) {
		super(Component.translatable("screen.orespawn.ore_entry"));
		this.parent = parent;
		this.session = session;
		this.oreId = oreId;
		enabled = GeologyEditorSession.bool(session.ore(oreId), "enabled", true);
	}

	@Override
	protected void init() {
		JsonObject ore = session.ore(oreId);
		JsonObject dimensions = dimensions(ore);
		JsonObject selectors = selectors(ore);
		addRenderableWidget(OreSpawnScreenLayout.explain(
				CycleButton.onOffBuilder(enabled).create(width / 2 - 155, 48, 150, 20,
						Component.translatable("option.orespawn.enabled"), (button, value) -> {
							enabled = value;
							ore.addProperty("enabled", value);
						}), "tooltip.orespawn.enabled"));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 + 5, 48, 150, 20,
				Component.translatable("button.orespawn.reset"), button -> reset()));

		List<String> ids = new ArrayList<>(dimensions.keySet());
		ids.addAll(selectors.keySet());
		Collections.sort(ids);
		int listTop = 76;
		int pickerY = height - 76;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (pickerY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(OreSpawnScreenLayout.button(this, font,
					width / 2 - 155, listTop + (i * 24), 310, 20,
					Component.literal(id), button -> minecraft.gui.setScreen(
							new OreDimensionScreen(this, session, oreId, id))));
		}
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 155, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 105, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		dimensionId = addRenderableWidget(new EditBox(font, width / 2 - 55, controlsY, 150, 20,
				Component.translatable("option.orespawn.dimension")));
		dimensionId.setMaxLength(128);
		List<String> availableDimensions = session.availableDimensionIds();
		availableDimensions.add(0, BROAD_SELECTOR);
		String selectedDimension = selectedDimension(availableDimensions, dimensions, selectors);
		if (dimensionText.isEmpty()) dimensionText = selectedDimension;
		dimensionId.setValue(dimensionText);
		dimensionId.setResponder(value -> dimensionText = value);
		OreSpawnScreenLayout.explain(dimensionId, "tooltip.orespawn.available_dimension");
		addRenderableWidget(CycleButton.builder(this::dimensionName, selectedDimension)
				.withValues(availableDimensions)
				.withTooltip(value -> tooltip("tooltip.orespawn.available_dimension"))
				.create(width / 2 - 155, pickerY, 310, 20,
						Component.translatable("option.orespawn.available_dimension"),
						(button, value) -> dimensionId.setValue(value)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 + 100, controlsY, 55, 20,
				Component.translatable("button.orespawn.add"), button -> addDimension()));

		int bottom = height - 28;
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 155, bottom, 100, 20, CommonComponents.GUI_DONE,
				button -> onClose()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 + 55, bottom, 100, 20,
				Component.translatable("button.orespawn.remove"), button -> unassign()));
	}

	private void addDimension() {
		String id;
		try {
			id = Identifier.parse(dimensionId.getValue().trim()).toString();
		} catch (RuntimeException e) {
			return;
		}
		dimensionText = id;
		JsonObject dimensions = dimensions(session.ore(oreId));
		JsonObject target = BROAD_SELECTOR.equals(id) ? selectors(session.ore(oreId)) : dimensions;
		if (!target.has(id)) {
			target.add(id, defaultDimension(id));
		}
		minecraft.gui.setScreen(new OreDimensionScreen(this, session, oreId, id));
	}

	private static JsonObject defaultDimension(String id) {
		if ("minecraft:overworld".equals(id)) return GeologyEditorSession.defaultOreDimension();
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", "minecraft:the_end".equals(id) ? 256 : 128);
		rule.addProperty("frequency", 1.0D);
		rule.addProperty("quantity", 8);
		rule.addProperty("pattern", "vein");
		rule.addProperty("height_distribution", "uniform");
		rule.addProperty("spread", 8);
		rule.addProperty("vertical_spread", 4);
		rule.addProperty("node_size", 4);
		if ("minecraft:the_end".equals(id)) {
			JsonArray blocks = new JsonArray();
			blocks.add("minecraft:end_stone");
			rule.add("host_blocks", blocks);
		} else {
			JsonArray tags = new JsonArray();
			tags.add("minecraft:the_nether".equals(id)
					? "minecraft:base_stone_nether" : "minecraft:stone_ore_replaceables");
			rule.add("host_tags", tags);
		}
		return rule;
	}

	private String selectedDimension(List<String> available, JsonObject configured, JsonObject selectors) {
		if (available.contains(dimensionText)) return dimensionText;
		for (String id : available) {
			if (!configured.has(id) && !selectors.has(id)) return id;
		}
		return available.get(0);
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private void reset() {
		session.resetEntry("ores", oreId);
		enabled = GeologyEditorSession.bool(session.ore(oreId), "enabled", true);
		rebuildWidgets();
	}

	private void unassign() {
		session.disableOrRemoveOre(oreId);
		minecraft.gui.setScreen(parent);
	}

	private static JsonObject dimensions(JsonObject ore) {
		if (!ore.has("dimensions") || !ore.get("dimensions").isJsonObject()) {
			JsonObject result = new JsonObject();
			ore.add("dimensions", result);
			return result;
		}
		return ore.getAsJsonObject("dimensions");
	}

	private static JsonObject selectors(JsonObject ore) {
		if (!ore.has("dimension_selectors") || !ore.get("dimension_selectors").isJsonObject()) {
			JsonObject result = new JsonObject();
			ore.add("dimension_selectors", result);
			return result;
		}
		return ore.getAsJsonObject("dimension_selectors");
	}

	private Component dimensionName(String id) {
		if (BROAD_SELECTOR.equals(id)) {
			return Component.translatable("value.orespawn.dimension.all_except_nether_end");
		}
		if ("minecraft:overworld".equals(id)) {
			return Component.translatable("value.orespawn.dimension.overworld");
		}
		if ("minecraft:the_nether".equals(id)) {
			return Component.translatable("value.orespawn.dimension.the_nether");
		}
		if ("minecraft:the_end".equals(id)) {
			return Component.translatable("value.orespawn.dimension.the_end");
		}
		return Component.literal(id);
	}

	private net.minecraft.client.gui.components.Tooltip tooltip(String key) {
		return net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key));
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		graphics.centeredText(font, title, width / 2, 10, OreSpawnScreenLayout.TEXT_PRIMARY);
		Component blockName = Component.literal(
				session.materialBlockId(GeologyEditorSession.MaterialTab.ORES, oreId));
		graphics.centeredText(font, OreSpawnScreenLayout.fit(font, blockName, Math.min(390, width - 24)),
				width / 2, 25, OreSpawnScreenLayout.TEXT_SECONDARY);
		String source = GeologyEditorSession.string(session.ore(oreId), "source_provider",
				GeologyEditorSession.string(session.ore(oreId), "source_mod", ""));
		if (!source.isEmpty()) {
			graphics.centeredText(font, Component.literal("Source: " + source), width / 2, 36, OreSpawnScreenLayout.TEXT_MUTED);
		}

	}
}
