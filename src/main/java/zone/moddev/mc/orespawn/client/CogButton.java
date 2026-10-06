package zone.moddev.mc.orespawn.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.AbstractGui;

/** Compact settings button used in the Mods directory. */
final class CogButton extends Button {
	CogButton(int x, int y, IPressable onPress, Tooltip tooltip) {
		super(x, y, 22, 20, "", onPress, tooltip);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
		if (!visible) return;
		drawCog(currentPoseStack(), x + 11, y + 10, active);
	}

	static void drawCog(MatrixStack poseStack, int centerX, int centerY, boolean active) {
		int color = active ? 0xFFE0E0E0 : 0xFF777777;
		AbstractGui.fill(poseStack, centerX - 4, centerY - 4, centerX + 5, centerY + 5, color);
		AbstractGui.fill(poseStack, centerX - 1, centerY - 6, centerX + 2, centerY - 4, color);
		AbstractGui.fill(poseStack, centerX - 1, centerY + 5, centerX + 2, centerY + 7, color);
		AbstractGui.fill(poseStack, centerX - 6, centerY - 1, centerX - 4, centerY + 2, color);
		AbstractGui.fill(poseStack, centerX + 5, centerY - 1, centerX + 7, centerY + 2, color);
		AbstractGui.fill(poseStack, centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFF303030);
	}
}
