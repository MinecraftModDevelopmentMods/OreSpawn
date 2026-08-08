package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;

final class OreEntryScreen extends Screen {
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
		super(new TranslatableComponent("screen.orespawn.ore_entry"));
		this.parent = parent;
		this.session = session;
		this.oreId = oreId;
		enabled = GeologyEditorSession.bool(session.ore(oreId), "enabled", true);
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		JsonObject ore = session.ore(oreId);
		JsonObject dimensions = dimensions(ore);
		JsonObject selectors = selectors(ore);
		OreSpawnScreenLayout.explain(this,
				addRenderableWidget(CycleButton.onOffBuilder(enabled).create(width / 2 - 155, 48, 150, 20,
						new TranslatableComponent("option.orespawn.enabled"), (button, value) -> {
							enabled = value;
							ore.addProperty("enabled", value);
						})), "tooltip.orespawn.enabled");
		addRenderableWidget(new Button(width / 2 + 5, 48, 150, 20,
				new TranslatableComponent("button.orespawn.reset"), button -> reset()));

		List<String> ids = new ArrayList<>(JsonCopies.keys(dimensions));
		ids.addAll(JsonCopies.keys(selectors));
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
					new TextComponent(id), button -> minecraft.setScreen(
							new OreDimensionScreen(this, session, oreId, id))));
		}
		Button previous = addRenderableWidget(new Button(width / 2 - 155, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(width / 2 - 105, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		dimensionId = addRenderableWidget(new EditBox(font, width / 2 - 55, controlsY, 150, 20,
				new TranslatableComponent("option.orespawn.dimension")));
		dimensionId.setMaxLength(128);
		List<String> availableDimensions = session.availableDimensionIds();
		availableDimensions.add(0, BROAD_SELECTOR);
		String selectedDimension = selectedDimension(availableDimensions, dimensions, selectors);
		if (dimensionText.isEmpty()) dimensionText = selectedDimension;
		dimensionId.setValue(dimensionText);
		dimensionId.setResponder(value -> dimensionText = value);
		OreSpawnScreenLayout.explain(this, dimensionId, "tooltip.orespawn.available_dimension");
		OreSpawnScreenLayout.explain(this, addRenderableWidget(CycleButton.builder(this::dimensionName)
				.withValues(availableDimensions)
				.withInitialValue(selectedDimension)
				.create(width / 2 - 155, pickerY, 310, 20,
						new TranslatableComponent("option.orespawn.available_dimension"),
						(button, value) -> dimensionId.setValue(value))),
				"tooltip.orespawn.available_dimension");
		addRenderableWidget(new Button(width / 2 + 100, controlsY, 55, 20,
				new TranslatableComponent("button.orespawn.add"), button -> addDimension()));

		int bottom = height - 28;
		addRenderableWidget(new Button(width / 2 - 155, bottom, 100, 20, CommonComponents.GUI_DONE,
				button -> onClose()));
		addRenderableWidget(new Button(width / 2 + 55, bottom, 100, 20,
				new TranslatableComponent("button.orespawn.remove"), button -> unassign()));
	}

	private void addDimension() {
		String id;
		try {
			id = new ResourceLocation(dimensionId.getValue().trim()).toString();
		} catch (RuntimeException e) {
			return;
		}
		dimensionText = id;
		JsonObject dimensions = dimensions(session.ore(oreId));
		JsonObject target = BROAD_SELECTOR.equals(id) ? selectors(session.ore(oreId)) : dimensions;
		if (!target.has(id)) {
			target.add(id, defaultDimension(id));
		}
		minecraft.setScreen(new OreDimensionScreen(this, session, oreId, id));
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

	private void rebuildWidgets() {
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
		minecraft.setScreen(parent);
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
			return new TranslatableComponent("value.orespawn.dimension.all_except_nether_end");
		}
		if ("minecraft:overworld".equals(id)) {
			return new TranslatableComponent("value.orespawn.dimension.overworld");
		}
		if ("minecraft:the_nether".equals(id)) {
			return new TranslatableComponent("value.orespawn.dimension.the_nether");
		}
		if ("minecraft:the_end".equals(id)) {
			return new TranslatableComponent("value.orespawn.dimension.the_end");
		}
		return new TextComponent(id);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 10, 0xFFFFFF);
		Component blockName = new TextComponent(
				session.materialBlockId(GeologyEditorSession.MaterialTab.ORES, oreId));
		drawCenteredString(poseStack, font, OreSpawnScreenLayout.fit(font, blockName, Math.min(390, width - 24)),
				width / 2, 25, 0xDDDDDD);
		String source = GeologyEditorSession.string(session.ore(oreId), "source_provider",
				GeologyEditorSession.string(session.ore(oreId), "source_mod", ""));
		if (!source.isEmpty()) {
			drawCenteredString(poseStack, font, new TextComponent("Source: " + source), width / 2, 36, 0xAAAAAA);
		}
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
