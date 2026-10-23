package zone.moddev.mc.orespawn.client;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

class OreSpawnScreenLayoutTest {
	@Test
	void biomeStatusTextIsFullyOpaque() throws ReflectiveOperationException {
		var color = BiomeWorldMaterialsScreen.class.getDeclaredMethod(
				"statusColor", BiomeDirectoryModel.Status.class);
		color.setAccessible(true);
		for (BiomeDirectoryModel.Status status : BiomeDirectoryModel.Status.values()) {
			assertEquals(255, ((Integer) color.invoke(null, status)) >>> 24, status.name());
		}
	}

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
		assertEquals(TabNavigationBar.class, CreateWorldScreen.class
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
		assertRowsClearFooter(265);
		assertRowsClearFooter(270);
	}

	@Test
	void worldCreationGridFitsWithAndWithoutTemplateRow() {
		for (int height : new int[] { 170, 180, 183, 205, 208 }) {
			for (int rows : new int[] { 7, 8 }) {
				int spacing = OreSpawnScreenLayout.worldCreationRowSpacing(height, rows);
				assertTrue(rows * 20 + (rows - 1) * spacing <= height - 2);
			}
		}
		assertEquals(4, OreSpawnScreenLayout.worldCreationRowSpacing(180, 7));
		assertEquals(2, OreSpawnScreenLayout.worldCreationRowSpacing(180, 8));
		assertEquals(300, OreSpawnScreenLayout.worldCreationContentWidth(320));
		assertEquals(385, OreSpawnScreenLayout.worldCreationContentWidth(426));
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtGuiScaleThree() {
		assertCompactOrePlacementClearsFooter(256);
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtMinimumTestHeight() {
		assertCompactOrePlacementClearsFooter(240);
	}

	@Test
	void oreSourcesKeepTwoCompactListsAtBothSupportedSizes() {
		assertEquals(406, OreSourceListScreen.contentWidth(426));
		assertEquals(174, OreSourceListScreen.leftPaneWidth(426));
		assertEquals(209, OreSourceListScreen.listHeight(265));
		assertEquals(300, OreSourceListScreen.contentWidth(320));
		assertEquals(129, OreSourceListScreen.leftPaneWidth(320));
		assertEquals(184, OreSourceListScreen.listHeight(240));
		assertEquals(80, OreSourceGroupSettingsScreen.aliasListHeight(265));
		assertEquals(64, OreSourceGroupSettingsScreen.aliasListHeight(240));
		assertEquals(58, OreSourceListScreen.compactFilterWidth(110, 48));
	}

	@Test
	void biomeDirectoryUsesPagesOnlyAtTheMinimumSize() {
		assertTrue(426 >= BiomeWorldMaterialsScreen.TWO_PANE_MINIMUM);
		assertTrue(320 < BiomeWorldMaterialsScreen.TWO_PANE_MINIMUM);
		assertTrue(BiomeWorldMaterialsScreen.listHeight(265) > 0);
		assertTrue(BiomeWorldMaterialsScreen.listHeight(240) > 0);
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
