package zone.moddev.mc.orespawn.client;

import net.minecraft.network.chat.Component;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;

/** Less commonly changed numeric controls, kept off the world settings overview. */
final class AdvancedGeologySettingsScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;

	AdvancedGeologySettingsScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.advanced"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int top = 54;
		int row = 0;
		if (session.hasTerrainRules()) {
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, top + (row++ * 28), 310, 20,
					Component.translatable("button.orespawn.formation_details"),
					button -> openNumeric("formations.custom", NumericConfigScreen.FORMATION_FIELDS)));
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, top + (row++ * 28), 310, 20,
					Component.translatable("button.orespawn.cyano_details"),
					button -> openNumeric("cyano", NumericConfigScreen.CYANO_FIELDS)));
		}
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, top + (row * 28), 310, 20,
				Component.translatable("button.orespawn.fluid_deposit_details"),
				button -> minecraft.setScreen(new FluidDepositListScreen(this, session))));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, OreSpawnScreenLayout.footerY(height), 150, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private void openNumeric(String path, NumericConfigScreen.Field[] fields) {
		minecraft.setScreen(new NumericConfigScreen(this, session, path, fields));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {

		graphics.drawCenteredString(font, title, width / 2, 20, OreSpawnScreenLayout.TEXT_PRIMARY);

	}
}
