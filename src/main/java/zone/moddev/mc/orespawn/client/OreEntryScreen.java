package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.ResourceLocation;

final class OreEntryScreen extends OreSpawnScreen {
	private static final String BROAD_SELECTOR =
			OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString();
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String oreId;
	private boolean enabled;
	private int page;
	private TextFieldWidget dimensionId;
	private String dimensionText = "";

	OreEntryScreen(GuiScreen parent, GeologyEditorSession session, String oreId) {
		super(new TextComponentTranslation("screen.orespawn.ore_entry"));
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
				addButton(CycleButton.onOffBuilder(enabled).create(width / 2 - 155, 48, 150, 20,
						new TextComponentTranslation("option.orespawn.enabled"), (button, value) -> {
							enabled = value;
							ore.addProperty("enabled", value);
						})), "tooltip.orespawn.enabled");
		addButton(new Button(width / 2 + 5, 48, 150, 20,
				new TextComponentTranslation("button.orespawn.reset"), button -> reset()));

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
			addButton(OreSpawnScreenLayout.button(this, font,
					width / 2 - 155, listTop + (i * 24), 310, 20,
					new TextComponentString(id), button -> minecraft.displayGuiScreen(
							new OreDimensionScreen(this, session, oreId, id))));
		}
		Button previous = addButton(new Button(width / 2 - 155, controlsY, 45, 20,
				new TextComponentString("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(width / 2 - 105, controlsY, 45, 20,
				new TextComponentString(">"), button -> { page++; rebuildWidgets(); }));
		previous.enabled = page > 0;
		next.enabled = page + 1 < pageCount;
		dimensionId = addButton(new TextFieldWidget(font, width / 2 - 55, controlsY, 150, 20,
				new TextComponentTranslation("option.orespawn.dimension")));
		dimensionId.setMaxLength(128);
		List<String> availableDimensions = session.availableDimensionIds();
		availableDimensions.add(0, BROAD_SELECTOR);
		String selectedDimension = selectedDimension(availableDimensions, dimensions, selectors);
		if (dimensionText.isEmpty()) dimensionText = selectedDimension;
		dimensionId.setValue(dimensionText);
		dimensionId.func_212954_a(value -> dimensionText = value);
		OreSpawnScreenLayout.explain(this, dimensionId, "tooltip.orespawn.available_dimension");
		OreSpawnScreenLayout.explain(this, addButton(CycleButton.builder(this::dimensionName)
				.withValues(availableDimensions)
				.withInitialValue(selectedDimension)
				.create(width / 2 - 155, pickerY, 310, 20,
						new TextComponentTranslation("option.orespawn.available_dimension"),
						(button, value) -> dimensionId.setValue(value))),
				"tooltip.orespawn.available_dimension");
		addButton(new Button(width / 2 + 100, controlsY, 55, 20,
				new TextComponentTranslation("button.orespawn.add"), button -> addDimension()));

		int bottom = height - 28;
		addButton(new Button(width / 2 - 155, bottom, 100, 20, DialogTexts.GUI_DONE,
				button -> onClose()));
		addButton(new Button(width / 2 + 55, bottom, 100, 20,
				new TextComponentTranslation("button.orespawn.remove"), button -> unassign()));
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
		minecraft.displayGuiScreen(new OreDimensionScreen(this, session, oreId, id));
	}

	private static JsonObject defaultDimension(String id) {
		if ("minecraft:overworld".equals(id)) return GeologyEditorSession.defaultOreDimension();
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", "minecraft:the_end".equals(id) ? 255 : 128);
		rule.addProperty("frequency", 1.0D);
		rule.addProperty("quantity", 8);
		rule.addProperty("pattern", "vein");
		rule.addProperty("height_distribution", "uniform");
		rule.addProperty("spread", 8);
		rule.addProperty("vertical_spread", 4);
		rule.addProperty("node_size", 4);
		if ("minecraft:the_end".equals(id)) {
			JsonArray blocks = new JsonArray();
			blocks.add(new JsonPrimitive("minecraft:end_stone"));
			rule.add("host_blocks", blocks);
		} else {
			JsonArray tags = new JsonArray();
			tags.add(new JsonPrimitive("minecraft:the_nether".equals(id)
					? "forge:netherrack" : "forge:stone"));
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
		buttons.clear(); children.clear();
		init();
	}

	private void reset() {
		session.resetEntry("ores", oreId);
		enabled = GeologyEditorSession.bool(session.ore(oreId), "enabled", true);
		rebuildWidgets();
	}

	private void unassign() {
		session.disableOrRemoveOre(oreId);
		minecraft.displayGuiScreen(parent);
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

	private ITextComponent dimensionName(String id) {
		if (BROAD_SELECTOR.equals(id)) {
			return new TextComponentTranslation("value.orespawn.dimension.all_except_nether_end");
		}
		if ("minecraft:overworld".equals(id)) {
			return new TextComponentTranslation("value.orespawn.dimension.overworld");
		}
		if ("minecraft:the_nether".equals(id)) {
			return new TextComponentTranslation("value.orespawn.dimension.the_nether");
		}
		if ("minecraft:the_end".equals(id)) {
			return new TextComponentTranslation("value.orespawn.dimension.the_end");
		}
		return new TextComponentString(id);
	}

	@Override
	public void onClose() {
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
		ITextComponent blockName = new TextComponentString(
				session.materialBlockId(GeologyEditorSession.MaterialTab.ORES, oreId));
		drawCenteredString(font, OreSpawnScreenLayout.fit(font, blockName, Math.min(390, width - 24)),
				width / 2, 25, 0xDDDDDD);
		String source = GeologyEditorSession.string(session.ore(oreId), "source_provider",
				GeologyEditorSession.string(session.ore(oreId), "source_mod", ""));
		if (!source.isEmpty()) {
			drawCenteredString(font, new TextComponentString("Source: " + source), width / 2, 36, 0xAAAAAA);
		}
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
