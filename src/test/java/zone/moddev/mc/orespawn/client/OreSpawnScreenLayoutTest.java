package zone.moddev.mc.orespawn.client;

import static java.lang.reflect.Modifier.isFinal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

class OreSpawnScreenLayoutTest {
	@Test
	void sharedScreenOwnsTheFinalRenderOrder() throws NoSuchMethodException {
		assertTrue(isFinal(OreSpawnScreen.class
				.getDeclaredMethod("render", GuiGraphics.class, int.class, int.class, float.class)
				.getModifiers()));
	}

	@Test
	void sharedScreenUsesTargetNativeBackgroundBeforeForeground() {
		List<String> passes = new ArrayList<>();
		OreSpawnScreen screen = new OreSpawnScreen(Component.empty()) {
			@Override
			public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
					float partialTick) {
				passes.add("background");
			}

			@Override
			protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY,
					float partialTick) {
				passes.add("foreground");
			}
		};

		screen.render(null, 0, 0, 0.0F);
		assertEquals(List.of("background", "foreground"), passes);
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
	void helpSharesTheRecommendedDefaultsHeaderInBothMainEditors() throws IOException {
		for (String screen : List.of("OreSpawnWorldCreationTab", "OreSpawnWorldSettingsScreen")) {
			String source = Files.readString(Path.of(
					"src/main/java/zone/moddev/mc/orespawn/client/" + screen + ".java"));
			int recommended = source.indexOf("button.orespawn.recommended");
			int help = source.indexOf("button.orespawn.help");
			int body = source.indexOf(screen.endsWith("Tab")
					? "buildStandaloneControls(row)" : "if (!terrain)", recommended);
			assertTrue(recommended >= 0 && help > recommended && help < body,
					"Help belongs beside Recommended Defaults, above the main controls: " + screen);
			assertEquals(help, source.lastIndexOf("button.orespawn.help"),
					"The editor should have one Help button, not a second footer row");
		}
	}

	@Test
	void compactMainRowsStayAboveFooter() {
		assertRowsClearFooter(240);
	}

	@Test
	void worldCreationGridFitsWithAndWithoutTheTemplateRow() {
		for (int availableHeight : new int[] {170, 180, 183, 205, 208}) {
			for (int rows : new int[] {7, 8}) {
				int spacing = OreSpawnScreenLayout.worldCreationRowSpacing(availableHeight, rows);
				assertTrue(rows * 20 + (rows - 1) * spacing <= availableHeight - 2,
						"The grid must stop before Minecraft's footer separator");
			}
		}
		assertEquals(4, OreSpawnScreenLayout.worldCreationRowSpacing(180, 7));
		assertEquals(2, OreSpawnScreenLayout.worldCreationRowSpacing(180, 8));
		assertEquals(300, OreSpawnScreenLayout.worldCreationContentWidth(320));
		assertEquals(385, OreSpawnScreenLayout.worldCreationContentWidth(426));
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
