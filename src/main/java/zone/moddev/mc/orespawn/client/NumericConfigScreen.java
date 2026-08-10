package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

final class NumericConfigScreen extends OreSpawnScreen {
	static final Field[] FORMATION_FIELDS = {
			new Field("stratum_wavelength", 16, 8192, false),
			new Field("family_region_wavelength", 16, 8192, false),
			new Field("vertical_thickness", 1, 192, true),
			new Field("waviness_wavelength", 32, 2048, false),
			new Field("waviness_amplitude", 0, 512, false),
			new Field("edge_wavelength", 8, 512, false),
			new Field("edge_amplitude", 0, 256, false),
			new Field("edge_octaves", 1, 8, true),
			new Field("continuity", 0, 1, false)
	};
	static final Field[] CYANO_FIELDS = {
			new Field("geome_size", 4, Short.MAX_VALUE, true),
			new Field("rock_layer_noise", 1, Short.MAX_VALUE, false),
			new Field("rock_layer_thickness", 1, 255, true)
	};

	private static final int PAGE_SIZE = 6;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String path;
	private final Field[] fields;
	private final List<TextFieldWidget> editors = new ArrayList<>();
	private int page;
	private ITextComponent error;

	NumericConfigScreen(GuiScreen parent, GeologyEditorSession session, String path, Field[] fields) {
		super(new TextComponentTranslation("screen.orespawn.numeric_settings"));
		this.parent = parent;
		this.session = session;
		this.path = path;
		this.fields = fields;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		editors.clear();
		JsonObject section = resolveSection();
		int start = page * PAGE_SIZE;
		int end = Math.min(fields.length, start + PAGE_SIZE);
		int left = width / 2 + 5;
		for (int i = start; i < end; i++) {
			Field field = fields[i];
			TextFieldWidget editor = new TextFieldWidget(font, left, 44 + ((i - start) * 25), 145, 20,
					new TextComponentString(field.key));
			editor.setMaxLength(32);
			JsonElement value = section.get(field.key);
			editor.setValue(value == null ? "0" : value.getAsString());
			editors.add(OreSpawnScreenLayout.explain(this, addButton(editor),
					"tooltip.orespawn.numeric." + field.key));
		}

		int bottom = height - 28;
		addButton(new Button(width / 2 - 155, bottom, 100, 20, DialogTexts.GUI_DONE,
				button -> saveAndClose()));
		addButton(new Button(width / 2 + 55, bottom, 100, 20, DialogTexts.GUI_CANCEL,
				button -> onClose()));
		Button previous = addButton(new Button(width / 2 - 50, bottom, 45, 20,
				new TextComponentString("<"), button -> changePage(-1)));
		Button next = addButton(new Button(width / 2 + 5, bottom, 45, 20,
				new TextComponentString(">"), button -> changePage(1)));
		previous.enabled = page > 0;
		next.enabled = (page + 1) * PAGE_SIZE < fields.length;
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
		if (savePage()) {
			minecraft.displayGuiScreen(parent);
		}
	}

	private boolean savePage() {
		JsonObject section = resolveSection();
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			Field field = fields[start + i];
			try {
				double value = Double.parseDouble(editors.get(i).getValue().trim());
				if (value < field.min || value > field.max || (field.integer && value != Math.rint(value))) {
					throw new NumberFormatException();
				}
				if (field.integer) section.addProperty(field.key, (int) value);
				else section.addProperty(field.key, value);
			} catch (NumberFormatException e) {
				error = new TextComponentString("Invalid value for " + field.key);
				return false;
			}
		}
		error = null;
		return true;
	}

	private JsonObject resolveSection() {
		JsonObject current = session.root();
		for (String segment : path.split("\\.")) {
			if (!current.has(segment) || !current.get(segment).isJsonObject()) {
				JsonObject child = new JsonObject();
				current.add(segment, child);
				current = child;
			} else {
				current = current.getAsJsonObject(segment);
			}
		}
		return current;
	}

	@Override
	public void onClose() {
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
		int start = page * PAGE_SIZE;
		for (int i = 0; i < editors.size(); i++) {
			drawString(font, label(fields[start + i].key), width / 2 - 155,
					50 + (i * 25), 0xDDDDDD);
		}
		if (error != null) {
			drawCenteredString(font, error, width / 2, height - 42, 0xFF5555);
		}
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}

	private ITextComponent label(String key) {
		return new TextComponentTranslation("option.orespawn." + key);
	}

	static final class Field {
		final String key;
		final double min;
		final double max;
		final boolean integer;

		Field(String key, double min, double max, boolean integer) {
			this.key = key;
			this.min = min;
			this.max = max;
			this.integer = integer;
		}
	}
}
