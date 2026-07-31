package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.worldgen.RockFamily;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

final class BlockAssignmentScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final String blockId;
	private Component error;

	BlockAssignmentScreen(Screen parent, GeologyEditorSession session, String blockId) {
		super(new TranslatableComponent("screen.orespawn.assign_block"));
		this.parent = parent;
		this.session = session;
		String canonicalId = session.canonicalBlockId(blockId);
		this.blockId = canonicalId == null ? blockId : canonicalId;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		int left = width / 2 - 155;
		int right = width / 2 + 5;
		addRockButton(left, 70, "tab.orespawn.sedimentary", RockFamily.SEDIMENTARY);
		addRockButton(right, 70, "tab.orespawn.metamorphic", RockFamily.METAMORPHIC);
		addRockButton(left, 98, "value.orespawn.intrusive", RockFamily.IGNEOUS_INTRUSIVE);
		addRockButton(right, 98, "value.orespawn.volcanic", RockFamily.IGNEOUS_VOLCANIC);
		OreSpawnScreenLayout.explain(this, addRenderableWidget(new Button(left, 126, 310, 20,
				new TranslatableComponent("tab.orespawn.ores"), button -> assignOre())),
				"tooltip.orespawn.assignment.ore");
		addRenderableWidget(new Button(width / 2 - 75, height - 28, 150, 20, CommonComponents.GUI_CANCEL,
				button -> onClose()));
	}

	private void addRockButton(int x, int y, String labelKey, RockFamily family) {
		OreSpawnScreenLayout.explain(this, addRenderableWidget(new Button(x, y, 150, 20,
				new TranslatableComponent(labelKey), button -> assignRock(family))),
				"tooltip.orespawn.assignment.rock_family");
	}

	private void assignRock(RockFamily family) {
		session.assignRock(blockId, family);
		if (session.section("rocks").has(blockId)) minecraft.setScreen(parent);
		else error = new TextComponent("Unknown or unsuitable block: " + blockId);
	}

	private void assignOre() {
		session.assignOre(blockId);
		if (session.section("ores").has(blockId)) minecraft.setScreen(parent);
		else error = new TextComponent("Unknown or unsuitable block: " + blockId);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 18, 0xFFFFFF);
		drawCenteredString(poseStack, font, new TextComponent(blockId), width / 2, 42, 0xDDDDDD);
		if (error != null) drawCenteredString(poseStack, font, error, width / 2, 155, 0xFF5555);
		super.render(poseStack, mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, poseStack, mouseX, mouseY);
	}
}
