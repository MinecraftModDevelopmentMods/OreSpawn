package zone.moddev.mc.orespawn.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.TranslationTextComponent;

/** Less commonly changed numeric controls, kept off the world settings overview. */
final class AdvancedGeologySettingsScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;

	AdvancedGeologySettingsScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslationTextComponent("screen.orespawn.advanced"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int left = width / 2 - 155;
		int top = 54;
		int row = 0;
		if (session.hasTerrainRules()) {
			addButton(OreSpawnScreenLayout.explainedButton(this, font,
					left, top + (row++ * 28), 310, 20,
					new TranslationTextComponent("button.orespawn.formation_details"),
					button -> openNumeric("formations.custom", NumericConfigScreen.FORMATION_FIELDS),
					"tooltip.orespawn.advanced.formations"));
			addButton(OreSpawnScreenLayout.explainedButton(this, font,
					left, top + (row++ * 28), 310, 20,
					new TranslationTextComponent("button.orespawn.cyano_details"),
					button -> openNumeric("cyano", NumericConfigScreen.CYANO_FIELDS),
					"tooltip.orespawn.advanced.cyano"));
		}
		if (!session.fluidDepositIds().isEmpty()) {
			addButton(OreSpawnScreenLayout.explainedButton(this, font,
					left, top + (row * 28), 310, 20,
					new TranslationTextComponent("button.orespawn.fluid_deposit_details"),
					button -> minecraft.displayGuiScreen(new FluidDepositListScreen(this, session)),
					"tooltip.orespawn.advanced.fluid_deposits"));
		}
		addButton(new Button(width / 2 - 75, OreSpawnScreenLayout.footerY(height), 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void openNumeric(String path, NumericConfigScreen.Field[] fields) {
		minecraft.displayGuiScreen(new NumericConfigScreen(this, session, path, fields));
	}

	@Override
	public void onClose() {
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
