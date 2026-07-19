package com.mcmoddev.orespawn.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TranslatableComponent;

/** Less commonly changed numeric controls, kept off the world settings overview. */
final class AdvancedGeologySettingsScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;

	AdvancedGeologySettingsScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslatableComponent("screen.orespawn.advanced"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int top = 54;
		addRenderableWidget(new Button(left, top, 310, 20,
				new TranslatableComponent("button.orespawn.formation_details"),
				button -> openNumeric("formations.custom", NumericConfigScreen.FORMATION_FIELDS)));
		addRenderableWidget(new Button(left, top + 28, 310, 20,
				new TranslatableComponent("button.orespawn.oil_details"),
				button -> openNumeric("oil", NumericConfigScreen.OIL_FIELDS)));
		addRenderableWidget(new Button(left, top + 56, 310, 20,
				new TranslatableComponent("button.orespawn.cyano_details"),
				button -> openNumeric("cyano", NumericConfigScreen.CYANO_FIELDS)));
		addRenderableWidget(new Button(width / 2 - 75, OreSpawnScreenLayout.footerY(height), 150, 20,
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
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 20, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
