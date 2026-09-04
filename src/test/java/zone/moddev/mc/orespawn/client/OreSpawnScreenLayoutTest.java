package zone.moddev.mc.orespawn.client;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphics;

class OreSpawnScreenLayoutTest {
	@Test
	void sharedScreenOwnsTheFinalRenderOrder() throws NoSuchMethodException {
		assertTrue(isFinal(OreSpawnScreen.class
				.getDeclaredMethod("render", GuiGraphics.class, int.class, int.class, float.class)
				.getModifiers()));
	}

	@Test
	void sharedScreenUsesTargetNativeBackgroundBeforeForeground() throws IOException {
		String source = Files.readString(Path.of(
				"src/main/java/zone/moddev/mc/orespawn/client/OreSpawnScreen.java"));
		int render = source.indexOf("public final void render(");
		int backgroundAndWidgets = source.indexOf("super.render(graphics", render);
		int foreground = source.indexOf("renderForeground(graphics", render);
		assertTrue(render >= 0 && backgroundAndWidgets > render && foreground > backgroundAndWidgets,
				"The 1.21.11 Screen render pass must finish before OreSpawn foreground text");
	}

	@Test
	void customScreenTextColorsAreFullyOpaque() {
		int[] colors = {
				OreSpawnScreenLayout.TEXT_PRIMARY,
				OreSpawnScreenLayout.TEXT_SECONDARY,
				OreSpawnScreenLayout.TEXT_SOFT,
				OreSpawnScreenLayout.TEXT_MUTED,
				OreSpawnScreenLayout.TEXT_BODY,
				OreSpawnScreenLayout.TEXT_HIGHLIGHT,
				OreSpawnScreenLayout.TEXT_ERROR
		};
		for (int color : colors) {
			assertEquals(0xFF000000, color & 0xFF000000);
		}
	}

	@Test
	void compactMainRowsStayAboveFooter() {
		assertRowsClearFooter(240);
	}

	@Test
	void normalMainRowsStayAboveFooter() {
		assertRowsClearFooter(270);
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtGuiScaleThree() {
		assertCompactOrePlacementClearsFooter(256);
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtMinimumTestHeight() {
		assertCompactOrePlacementClearsFooter(240);
	}

	private static void assertRowsClearFooter(int height) {
		int lastRowBottom = OreSpawnScreenLayout.mainTop(height)
				+ (OreSpawnScreenLayout.mainRowSpacing(height) * 7) + 20;
		assertTrue(lastRowBottom < OreSpawnScreenLayout.footerY(height));
	}

	private static void assertCompactOrePlacementClearsFooter(int height) {
		int lastFieldBottom = OreSpawnScreenLayout.compactOrePlacementFieldY(height, 2) + 20;
		assertTrue(lastFieldBottom < OreSpawnScreenLayout.footerY(height));
	}
}
