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
		assertEquals(25, screens.size(), "Review this render-order gate when screens are added or removed");
		for (Path screen : screens) {
			String source = new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
			int render = source.indexOf(
					"public void render(int mouseX, int mouseY, float partialTick)");
			int background = source.indexOf("renderBackground();", render);
			int widgets = source.indexOf("super.render(mouseX, mouseY, partialTick);", render);
			String name = screen.getFileName().toString();
			assertTrue(render >= 0, name + " must own its 1.12.2 render pass");
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
	void advancedAndFluidEditorsShareTheRecoveredTerrainRow() throws Exception {
		Path screen = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", "OreSpawnWorldSettingsScreen.java");
		String source = new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
		int advanced = source.indexOf("new TextComponentTranslation(\"button.orespawn.advanced\")");
		int advancedCall = source.lastIndexOf("addButton", advanced);
		int fluid = source.indexOf("fluidEditorLabel()", advanced);
		int fluidCall = source.lastIndexOf("addButton", fluid);
		assertTrue(advanced > 0 && fluid > advanced);
		assertTrue(source.substring(advancedCall, advanced)
				.contains("font, left, top + (row * rowIndex)"));
		assertTrue(source.substring(fluidCall, fluid)
				.contains("font, right, top + (row * rowIndex++)"));
	}

	private static void assertRowsClearFooter(int height) {
		int rows = 8;
		int top = OreSpawnScreenLayout.mainTop(height);
		int available = OreSpawnScreenLayout.footerY(height) - top - 20 - 4;
		int spacing = Math.min(OreSpawnScreenLayout.mainRowSpacing(height),
				Math.max(20, available / (rows - 1)));
		int lastRowBottom = OreSpawnScreenLayout.mainTop(height)
				+ (spacing * (rows - 1)) + 20;
		assertTrue(lastRowBottom < OreSpawnScreenLayout.footerY(height));
	}

	private static void assertCompactOrePlacementClearsFooter(int height) {
		int lastFieldBottom = OreSpawnScreenLayout.compactOrePlacementFieldY(height, 2) + 20;
		assertTrue(lastFieldBottom < OreSpawnScreenLayout.footerY(height));
	}
}
