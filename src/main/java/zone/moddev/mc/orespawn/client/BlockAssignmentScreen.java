package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.worldgen.RockFamily;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class BlockAssignmentScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String blockId;
	private Component error;

	BlockAssignmentScreen(Screen parent, GeologyEditorSession session, String blockId) {
		super(Component.translatable("screen.orespawn.assign_block"));
		this.parent = parent;
		this.session = session;
		String canonicalId = session.canonicalBlockId(blockId);
		this.blockId = canonicalId == null ? blockId : canonicalId;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, 70, 150, 20, Component.translatable("tab.orespawn.sedimentary"),
				button -> assignRock(RockFamily.SEDIMENTARY)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(right, 70, 150, 20, Component.translatable("tab.orespawn.metamorphic"),
				button -> assignRock(RockFamily.METAMORPHIC)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, 98, 150, 20, Component.translatable("value.orespawn.intrusive"),
				button -> assignRock(RockFamily.IGNEOUS_INTRUSIVE)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(right, 98, 150, 20, Component.translatable("value.orespawn.volcanic"),
				button -> assignRock(RockFamily.IGNEOUS_VOLCANIC)));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left, 126, 310, 20, Component.translatable("tab.orespawn.ores"),
				button -> assignOre()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
	}

	private void assignRock(RockFamily family) {
		session.assignRock(blockId, family);
		if (session.section("rocks").has(blockId)) minecraft.setScreen(parent);
		else error = Component.literal("Unknown or unsuitable block: " + blockId);
	}

	private void assignOre() {
		session.assignOre(blockId);
		if (session.section("ores").has(blockId)) minecraft.setScreen(parent);
		else error = Component.literal("Unknown or unsuitable block: " + blockId);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.literal(blockId), width / 2, 42, 0xDDDDDD);
		if (error != null) graphics.drawCenteredString(font, error, width / 2, 155, 0xFF5555);
		super.render(graphics, mouseX, mouseY, partialTick);
	}
}
