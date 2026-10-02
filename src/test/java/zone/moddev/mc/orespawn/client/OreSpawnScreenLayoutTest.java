package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class OreSpawnScreenLayoutTest {
	@Test
	void everyConcreteScreenClearsThePreviousFrameBeforeDrawingWidgets() throws Exception {
		Path directory = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client");
		List<Path> screens;
		try (Stream<Path> files = Files.list(directory)) {
			screens = files
					.filter(path -> path.getFileName().toString().endsWith("Screen.java"))
					.filter(path -> !path.getFileName().toString().equals("OreSpawnScreen.java"))
					.sorted()
					.collect(Collectors.toList());
		}
		assertEquals(28, screens.size(), "Review this render-order gate when screens are added or removed");
		for (Path screen : screens) {
			String source = new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
			int render = source.indexOf(
					"public void render(int mouseX, int mouseY, float partialTick)");
			int background = source.indexOf("renderBackground();", render);
			int widgets = source.indexOf("super.render(mouseX, mouseY, partialTick);", render);
			String name = screen.getFileName().toString();
			assertTrue(render >= 0, name + " must own its 1.14.4 render pass");
			assertTrue(background > render, name + " must clear the previous frame");
			assertTrue(widgets > background, name + " must clear before drawing widgets and tooltips");
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
