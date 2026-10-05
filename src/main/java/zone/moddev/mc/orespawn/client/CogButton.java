package zone.moddev.mc.orespawn.client;


/** A small cog button for opening settings. */
final class CogButton extends Button {
	CogButton(int x, int y, IPressable onPress, Tooltip tooltip) {
		super(x, y, 22, 20, "", onPress, tooltip);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		super.render(mouseX, mouseY, partialTick);
		if (!visible) return;
		drawCog(x + 11, y + 10, active);
	}

	static void drawCog(int centerX, int centerY, boolean active) {
		int color = active ? 0xFFE0E0E0 : 0xFF777777;
		fill(centerX - 4, centerY - 4, centerX + 5, centerY + 5, color);
		fill(centerX - 1, centerY - 6, centerX + 2, centerY - 4, color);
		fill(centerX - 1, centerY + 5, centerX + 2, centerY + 7, color);
		fill(centerX - 6, centerY - 1, centerX - 4, centerY + 2, color);
		fill(centerX + 5, centerY - 1, centerX + 7, centerY + 2, color);
		fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xFF303030);
	}
}
