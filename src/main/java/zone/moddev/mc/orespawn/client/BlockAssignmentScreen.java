package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.worldgen.RockFamily;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

final class BlockAssignmentScreen extends OreSpawnScreen {
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
		addRockButton(left, 70, "tab.orespawn.sedimentary", RockFamily.SEDIMENTARY);
		addRockButton(right, 70, "tab.orespawn.metamorphic", RockFamily.METAMORPHIC);
		addRockButton(left, 98, "value.orespawn.intrusive", RockFamily.IGNEOUS_INTRUSIVE);
		addRockButton(right, 98, "value.orespawn.volcanic", RockFamily.IGNEOUS_VOLCANIC);
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.plainButton(
				left, 126, 310, 20, Component.translatable("tab.orespawn.ores"), button -> assignOre()),
				"tooltip.orespawn.assignment.ore"));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
	}

	private void addRockButton(int x, int y, String labelKey, RockFamily family) {
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.plainButton(
				x, y, 150, 20, Component.translatable(labelKey), button -> assignRock(family)),
				"tooltip.orespawn.assignment.rock_family"));
	}

	private void assignRock(RockFamily family) {
		session.assignRock(blockId, family);
		if (session.section("rocks").has(blockId)) minecraft.gui.setScreen(parent);
		else error = Component.literal("Unknown or unsuitable block: " + blockId);
	}

	private void assignOre() {
		session.assignOre(blockId);
		if (session.section("ores").has(blockId)) minecraft.gui.setScreen(parent);
		else error = Component.literal("Unknown or unsuitable block: " + blockId);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

		graphics.centeredText(font, title, width / 2, 18, OreSpawnScreenLayout.TEXT_PRIMARY);
		graphics.centeredText(font, Component.literal(blockId), width / 2, 42, OreSpawnScreenLayout.TEXT_SECONDARY);
		if (error != null) graphics.centeredText(font, error, width / 2, 155, OreSpawnScreenLayout.TEXT_ERROR);

	}
}
