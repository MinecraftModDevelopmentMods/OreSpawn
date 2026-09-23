package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import zone.moddev.mc.orespawn.client.BiomeDirectoryModel.Palette;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Lists every palette in effective profile order for one dimension. */
final class BiomePaletteScreen extends OreSpawnScreen {
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String dimension;
	private CompactScrollList list;
	private int scroll;
	private String armedPalette;
	private int resetScope;

	BiomePaletteScreen(GuiScreen parent, GeologyEditorSession session, String dimension) {
		super(new TextComponentTranslation("screen.orespawn.biome_palettes"));
		this.parent = parent;
		this.session = session;
		this.dimension = dimension;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int contentWidth = Math.min(520, Math.max(300, width - 20));
		int left = (width - contentWidth) / 2;
		List<Palette> palettes = session.biomeDirectory().palettes(dimension);
		int listTop = 38;
		int listHeight = Math.max(32, height - listTop - 62);
		list = addButton(new CompactScrollList(this, left, listTop, contentWidth, listHeight) {
			@Override protected int size() { return palettes.size(); }
			@Override protected String rowText(int index) {
				Palette palette = palettes.get(index);
				return (palette.enabled ? "[x] " : "[ ] ") + (palette.order + 1) + ". " + palette.id;
			}
			@Override protected boolean rowEnabled(int index) { return !palettes.get(index).override; }
			@Override protected int rowColor(int index) {
				return palettes.get(index).override ? 0x55FFFF : palettes.get(index).enabled ? 0xFFFFFF : 0x888888;
			}
			@Override protected void onRowPressed(int index) {
				Palette palette = palettes.get(index);
				if (!palette.override) minecraft.displayGuiScreen(new BiomePaletteSettingsScreen(
						BiomePaletteScreen.this, session, palette.id));
			}
			@Override protected int rowActionWidth(int index) { return 18; }
			@Override protected void drawRowAction(Minecraft minecraft, int index, int x, int y,
					int width, int height, boolean hovered) {
				drawCenteredString(font, armedPalette != null && armedPalette.equals(palettes.get(index).id)
						? "!" : "R", x + width / 2, y + 4, hovered ? 0xFFFFFF : 0xAAAAAA);
			}
			@Override protected void onRowActionPressed(int index) { resetPalette(palettes.get(index)); }
			@Override protected List<String> rowTooltip(int index) {
				Palette palette = palettes.get(index);
				List<String> lines = new ArrayList<>();
				lines.add(palette.id);
				lines.add(net.minecraft.client.resources.I18n.format(
						"label.orespawn.biome.palette_owner_order", palette.owner, palette.order + 1));
				if (palette.override) lines.add(net.minecraft.client.resources.I18n.format(
						"tooltip.orespawn.biome.override_palette"));
				return lines;
			}
			@Override protected List<String> rowActionTooltip(int index) {
				return java.util.Collections.singletonList(net.minecraft.client.resources.I18n.format(
						armedPalette != null && armedPalette.equals(palettes.get(index).id)
								? "button.orespawn.biome.confirm_reset_palette"
								: "button.orespawn.biome.reset_palette"));
			}
		});
		list.setFirstIndex(scroll);
		int half = (contentWidth - 4) / 2;
		int resetY = height - 52;
		addButton(OreSpawnScreenLayout.button(this, font, left, resetY, half, 20,
				new TextComponentTranslation(resetScope == 1 ? "button.orespawn.biome.confirm_reset_dimension"
						: "button.orespawn.biome.reset_dimension"), button -> resetDimension()));
		addButton(OreSpawnScreenLayout.button(this, font, left + half + 4, resetY, half, 20,
				new TextComponentTranslation(resetScope == 2 ? "button.orespawn.biome.confirm_reset_all"
						: "button.orespawn.biome.reset_all"), button -> resetAll()));
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void resetPalette(Palette palette) {
		resetScope = 0;
		if (!palette.id.equals(armedPalette)) { armedPalette = palette.id; rebuild(); return; }
		session.resetBiomePalette(palette.id); armedPalette = null; rebuild();
	}

	private void resetDimension() {
		armedPalette = null;
		if (resetScope != 1) { resetScope = 1; rebuild(); return; }
		session.resetBiomeDimension(dimension); resetScope = 0; rebuild();
	}

	private void resetAll() {
		armedPalette = null;
		if (resetScope != 2) { resetScope = 2; rebuild(); return; }
		session.resetAllBiomeManagement(); resetScope = 0; rebuild();
	}

	private void rebuild() { if (list != null) scroll = list.firstIndex(); buttons.clear(); children.clear(); init(); }

	@Override public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void handleMouseInput() throws java.io.IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0 || list == null) return;
		int mouseX = Mouse.getEventX() * width / minecraft.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / minecraft.displayHeight - 1;
		if (list.mouseWheel(mouseX, mouseY, wheel)) scroll = list.firstIndex();
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
		drawCenteredString(font, new TextComponentString(dimension), width / 2, 23, 0xAAAAAA);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
