package zone.moddev.mc.orespawn.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

final class DimensionMaterialsScreen extends OreSpawnScreen {
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private TextFieldWidget deepY;

	DimensionMaterialsScreen(GuiScreen parent, GeologyEditorSession session, String dimension) {
		super(new TextComponentTranslation("screen.orespawn.dimension_materials"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int contentWidth = Math.min(390, Math.max(290, width - 24));
		int left = (width - contentWidth) / 2;
		JsonObject materials = session.dimensionMaterials(dimension, true);
		int y = 58;
		y = materialRow(left, y, contentWidth, materials, "default_fluid", true, true);
		y = materialRow(left, y, contentWidth, materials, "deep_aquifer_fluid", true, false);
		deepY = addButton(new TextFieldWidget(font, left + contentWidth / 2, y,
				contentWidth / 2, 20, new TextComponentTranslation("option.orespawn.deep_aquifer_y")));
		deepY.setValue(Integer.toString(integer(materials, "deep_aquifer_max_y", -54)));
		deepY.enabled = false;
		OreSpawnScreenLayout.explain(this, deepY, "tooltip.orespawn.material.deep_aquifer_y");
		addButton(new Button(left, y, contentWidth / 2 - 5, 20,
				new TextComponentTranslation("option.orespawn.deep_aquifer_y"), button -> { })).enabled = false;
		y += 26;
		y = materialRow(left, y, contentWidth, materials, "snow_block", false, true);
		materialRow(left, y, contentWidth, materials, "ice_block", false, true);
		addButton(new Button(left, OreSpawnScreenLayout.footerY(height),
				contentWidth, 20, DialogTexts.GUI_DONE, button -> { save(); onClose(); }));
	}

	private int materialRow(int left, int y, int width, JsonObject materials,
			String key, boolean fluid, boolean editable) {
		String value = string(materials, key, "");
		Button selector = addButton(OreSpawnScreenLayout.explainedButton(this, font,
				left, y, width - 65, 20,
				label(key, value), button -> {
					save();
					minecraft.displayGuiScreen(new MaterialBlockPickerScreen(this, session, fluid,
							id -> session.setMaterialBlock(dimension, key, id, fluid)));
				}, materialHelp(key)));
		selector.enabled = editable;
		Button clear = addButton(new Button(left + width - 60, y, 60, 20,
				new TextComponentTranslation("button.orespawn.clear"), button -> {
					session.setMaterialBlock(dimension, key, null, fluid);
					rebuildWidgets();
				}));
		clear.enabled = editable && !value.isEmpty();
		return y + 26;
	}

	private static String materialHelp(String key) {
		return "tooltip.orespawn.material." + key;
	}

	private ITextComponent label(String key, String value) {
		return new TextComponentTranslation("option.orespawn." + key,
				value.isEmpty() ? new TextComponentTranslation("value.orespawn.not_set")
						: new TextComponentString(value));
	}

	private void save() {
		// Minecraft 1.12.2 exposes one generator fluid. Retain stored deep-aquifer
		// fields unchanged so the same provider/profile can still be used by later ports.
	}

	private void rebuildWidgets() { buttons.clear(); children.clear(); init(); }
	@Override public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		drawCenteredString(font, new TextComponentString(dimension), width / 2, 30, 0xCCCCCC);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}
	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}
}
