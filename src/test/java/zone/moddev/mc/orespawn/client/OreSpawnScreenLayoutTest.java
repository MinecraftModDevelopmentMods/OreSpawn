package zone.moddev.mc.orespawn.client;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

class OreSpawnScreenLayoutTest {
	@Test
	void sharedScreenOwnsTheFinalRenderOrder() throws NoSuchMethodException {
		assertTrue(isFinal(OreSpawnScreen.class
				.getDeclaredMethod("extractRenderState", GuiGraphicsExtractor.class, int.class, int.class, float.class)
				.getModifiers()));
	}

	@Test
	void worldCreationReflectionTargetsMatchTheUnobfuscatedClient() throws NoSuchFieldException {
		assertEquals(TabManager.class, CreateWorldScreen.class
				.getDeclaredField(WorldCreationScreenHandler.TAB_MANAGER_FIELD).getType());
		assertEquals(MenuTabBar.class, CreateWorldScreen.class
				.getDeclaredField(WorldCreationScreenHandler.TAB_NAVIGATION_BAR_FIELD).getType());
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
