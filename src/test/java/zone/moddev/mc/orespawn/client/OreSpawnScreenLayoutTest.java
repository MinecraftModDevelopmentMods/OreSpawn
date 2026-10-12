package zone.moddev.mc.orespawn.client;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
	void sharedScreenUsesTargetNativeBackgroundBeforeForeground() throws IOException {
		String source = Files.readString(Path.of(
				"src/main/java/zone/moddev/mc/orespawn/client/OreSpawnScreen.java"));
		int render = source.indexOf("public final void extractRenderState(");
		int backgroundAndWidgets = source.indexOf("super.extractRenderState(graphics", render);
		int foreground = source.indexOf("renderForeground(graphics", render);
		assertTrue(render >= 0 && backgroundAndWidgets > render && foreground > backgroundAndWidgets,
				"The 26.3 Screen render pass must finish before OreSpawn foreground text");
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
	void editorColorsIncludeAnExplicitAlphaChannel() throws IOException {
		// The 26.3 text extractor drops RGB-only colours instead of making them opaque.
		var rgbOnly = java.util.regex.Pattern.compile("\\b0x[0-9a-fA-F]{6}\\b");
		try (var sources = Files.walk(Path.of("src/main/java/zone/moddev/mc/orespawn/client"))) {
			for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
				assertTrue(!rgbOnly.matcher(Files.readString(source)).find(),
						() -> source + " contains a colour without an alpha channel");
			}
		}
	}

	@Test
	void worldCreationHelpSharesTheRecommendedDefaultsHeader() throws IOException {
		String source = Files.readString(Path.of(
				"src/main/java/zone/moddev/mc/orespawn/client/OreSpawnWorldCreationTab.java"));
		int header = source.indexOf("private void buildControls()");
		int recommended = source.indexOf("button.orespawn.recommended", header);
		int help = source.indexOf("button.orespawn.help", header);
		int body = source.indexOf("buildStandaloneControls(row)", header);
		assertTrue(recommended > header && help > recommended && help < body,
				"Help belongs beside Recommended Defaults, not in the footer's last row");
	}

	@Test
	void worldCreationGridFitsWithAndWithoutTheTemplateRow() {
		for (int availableHeight : new int[] {180, 183, 205, 208}) {
			for (int rows : new int[] {7, 8}) {
				int spacing = OreSpawnScreenLayout.worldCreationRowSpacing(availableHeight, rows);
				assertTrue(rows * 20 + (rows - 1) * spacing <= availableHeight - 2,
						"The grid must stop before Minecraft's two-pixel footer separator");
			}
		}
		assertEquals(4, OreSpawnScreenLayout.worldCreationRowSpacing(180, 7));
		assertEquals(2, OreSpawnScreenLayout.worldCreationRowSpacing(180, 8));
		assertEquals(300, OreSpawnScreenLayout.worldCreationContentWidth(320));
		assertEquals(385, OreSpawnScreenLayout.worldCreationContentWidth(426));
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
