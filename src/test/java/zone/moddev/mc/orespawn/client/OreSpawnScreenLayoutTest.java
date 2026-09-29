package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
			assertTrue(render >= 0, name + " must own its 1.10.2 render pass");
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

	@Test
	void oreSourcesAndAddBlockShareTheCompactOreFooter() throws Exception {
		String source = screenSource("GeologyMaterialsScreen.java");
		int width = source.indexOf("int addWidth = tab == MaterialTab.ORES");
		int sources = source.indexOf("button.orespawn.ore_sources", width);
		int add = source.indexOf("button.orespawn.add_block", sources);
		assertTrue(width >= 0 && sources > width && add > sources);
		assertTrue(source.substring(width, add).contains("addX - addWidth - 5"));
		assertTrue(source.substring(width, add).contains("addX, controlsY, addWidth, 20"));
	}

	@Test
	void oreSourceEditorKeepsBothCompactScrollablePanesVisible() throws Exception {
		assertEquals(406, OreSourceListScreen.contentWidth(426));
		assertEquals(174, OreSourceListScreen.leftPaneWidth(426));
		assertEquals(209, OreSourceListScreen.listHeight(265));
		assertEquals(13, CompactScrollList.visibleRows(209, 16));
		assertEquals(300, OreSourceListScreen.contentWidth(320));
		assertEquals(129, OreSourceListScreen.leftPaneWidth(320));
		assertEquals(184, OreSourceListScreen.listHeight(240));
		assertEquals(11, CompactScrollList.visibleRows(184, 16));
		String list = screenSource("OreSourceListScreen.java");
		assertTrue(list.contains("initGroups("));
		assertTrue(list.contains("initOutputs("));
		assertTrue(list.contains("new CompactScrollList(this, leftPaneX, CONTENT_TOP"));
		assertTrue(list.contains("new CompactScrollList(this, rightPaneX, CONTENT_TOP + 24"));
		assertTrue(list.contains("label.orespawn.ore_source.groups"));
		assertTrue(list.contains("button.orespawn.show_all"));
		assertTrue(list.contains("button.orespawn.ore_source.hide_single"));
		assertTrue(list.contains("compactFilterWidth"));
		assertTrue(list.contains("button.orespawn.ore_source.accept"));
		assertTrue(list.contains("acceptOreSourcePolicy(group.key)"));
		assertTrue(list.contains("button.orespawn.ore_source.reset_all"));
		assertTrue(list.contains("button.orespawn.ore_source.confirm_reset_all"));
		assertTrue(list.contains("session.resetOreSourcesToDefaults()"));
		assertTrue(list.contains("leftPaneX, height - 28"));
		assertTrue(list.contains("rightPaneX + rightPaneWidth - doneWidth, height - 28"));
		assertEquals(58, OreSourceListScreen.compactFilterWidth(110, 48));
		assertTrue(list.contains("groupRowColor"));
		assertTrue(list.contains("drawCompactCog"));
		assertTrue(list.contains("outputCandidates()"));
		assertTrue(list.contains("single || !group.outputs.containsKey(candidate.sourceId)"),
				"Single output rows behave as radio buttons and cannot deselect their only output");
		assertTrue(list.contains("mouseWheel(mouseX, mouseY, wheel)"));
		assertTrue(list.contains("minecraft.displayGuiScreen(parent)"));
		assertFalse(list.contains("button.orespawn.ore_source.all_groups"));
		assertFalse(list.contains("button.orespawn.ore_source.advanced"));
		assertFalse(list.contains("groupPage"));
		assertFalse(list.contains("outputPage"));
		assertFalse(list.contains("nameField"));
		assertFalse(list.contains("aliasField"));
		assertFalse(list.contains("WIDE_MINIMUM"));
		assertFalse(list.contains("compactDetail"));
		assertFalse(list.contains("button.orespawn.back"));
		assertFalse(list.contains("button.orespawn.ore_source.needs_attention"));
		assertFalse(Files.exists(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", "OreSourceDetailScreen.java")),
				"The technical detail screen must not remain as a second competing flow");
	}

	@Test
	void groupSettingsOwnsAliasesAndClearlyNamedPlacementRules() throws Exception {
		assertEquals(80, OreSourceGroupSettingsScreen.aliasListHeight(265));
		assertEquals(64, OreSourceGroupSettingsScreen.aliasListHeight(240));
		assertEquals(101, OreSourceGroupSettingsScreen.doneButtonWidth(406));
		assertEquals(80, OreSourceGroupSettingsScreen.doneButtonWidth(240));
		assertEquals(120, OreSourceGroupSettingsScreen.doneButtonWidth(600));
		assertEquals(297, OreSourceGroupSettingsScreen.validationMessageWidth(406));
		String settings = screenSource("OreSourceGroupSettingsScreen.java");
		assertTrue(settings.contains("contentX + nameLabelWidth, NAME_TOP"));
		assertTrue(settings.contains("NAME_TOP + 6"));
		assertTrue(settings.contains("option.orespawn.ore_source.group_name"));
		assertTrue(settings.contains("option.orespawn.ore_source.alias"));
		assertTrue(settings.contains("label.orespawn.ore_source.placement_rules"));
		assertTrue(settings.contains("tooltip.orespawn.ore_source.placement_rules"));
		assertTrue(settings.contains("new TextComponentString(\"?\")"));
		assertTrue(settings.contains("label.orespawn.ore_source.standard_veins"));
		assertTrue(settings.contains("placementSelectable(group, channel)"));
		assertTrue(settings.contains("placementSourceLabel(source, choices.size())"));
		assertTrue(settings.contains("group.canDeleteEmpty()"));
		assertTrue(settings.contains("group.canDissolve()"));
		assertTrue(settings.contains("button.orespawn.ore_source.confirm_dissolve"));
		assertTrue(settings.contains("session.deleteEmptyOreMaterialGroup(group.material)"));
		assertTrue(settings.contains("session.dissolveOreMaterialGroup(group.material)"));
		assertFalse(settings.contains("session.deleteOreMaterialGroup(group.material)"));
		assertTrue(settings.contains("contentX + contentWidth - doneWidth, height - 28"));
		assertTrue(settings.contains("contentX + 2, height - 22, 0xFF5555"));
		assertTrue(settings.contains("renderStringTooltip(java.util.Collections.singletonList(error)"));
		assertFalse(settings.contains("width / 2 - 75, height - 28"));
		assertFalse(settings.contains("width / 2, 19, 0xFF5555"));
		assertTrue(screenSource("OreSourceListScreen.java").contains("!candidate.external"));
		assertTrue(settings.contains("minecraft.displayGuiScreen(parent)"));
		assertFalse(settings.contains("button.orespawn.ore_source.advanced"));
		assertFalse(settings.contains("placementExplanationLines"));
	}

	@Test
	void tooltipsFitBesideTheCursorInsteadOfRunningOffScreen() {
		assertEquals(260, OreSpawnScreen.tooltipWidth(426, 300));
		assertEquals(189, OreSpawnScreen.tooltipWidth(426, 213));
		assertEquals(136, OreSpawnScreen.tooltipWidth(320, 160));
		assertEquals(96, OreSpawnScreen.tooltipWidth(240, 120));
	}

	@Test
	void materialGroupDefaultsPersistOnlyThroughMainDone() throws Exception {
		String main = screenSource("OreSpawnWorldSettingsScreen.java");
		String groups = screenSource("OreSourceListScreen.java");
		int save = main.indexOf("private void saveAndClose()");
		int persistence = main.indexOf("GeomeConfig.persistOreMaterialGroups", save);
		assertTrue(save >= 0 && persistence > save);
		assertFalse(groups.contains("persistOreMaterialGroups"),
				"leaving Material Groups must not persist future-world defaults");
		assertTrue(groups.contains("minecraft.displayGuiScreen(parent)"),
				"Done and Escape return to the pending ORES editor session");
	}

	@Test
	void biomeDirectoryUsesTwoPanesAt426AndResponsivePagesAt320() throws Exception {
		List<String> dimensions = Arrays.asList(
				"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");
		assertEquals("minecraft:the_nether",
				BiomeWorldMaterialsScreen.nextDimension(dimensions, "minecraft:overworld"));
		assertEquals("minecraft:the_end",
				BiomeWorldMaterialsScreen.nextDimension(dimensions, "minecraft:the_nether"));
		assertEquals("minecraft:overworld",
				BiomeWorldMaterialsScreen.nextDimension(dimensions, "minecraft:the_end"));
		assertEquals(406, BiomeWorldMaterialsScreen.contentWidth(426));
		assertEquals(174, BiomeWorldMaterialsScreen.leftPaneWidth(426));
		assertEquals(161, BiomeWorldMaterialsScreen.listHeight(265));
		assertEquals(213, BiomeWorldMaterialsScreen.materialsButtonY(265));
		assertEquals(300, BiomeWorldMaterialsScreen.contentWidth(320));
		assertEquals(132, BiomeWorldMaterialsScreen.leftPaneWidth(320));
		assertEquals(136, BiomeWorldMaterialsScreen.listHeight(240));
		assertEquals(188, BiomeWorldMaterialsScreen.materialsButtonY(240));
		assertEquals(24, BiomeWorldMaterialsScreen.detailTop(false),
				"the two-pane detail must align with the top controls");
		assertEquals(34, BiomeWorldMaterialsScreen.detailTop(true),
				"the compact detail must remain below its Back button");
		assertEquals(95, BiomeWorldMaterialsScreen.detailRuleTop(false, false));
		assertEquals(119, BiomeWorldMaterialsScreen.detailRuleTop(false, true));
		assertEquals(139, BiomeWorldMaterialsScreen.detailRuleTop(false, true, 2),
				"a two-line replacement validation error must reserve space above Placement Rules");
		assertEquals(107, BiomeWorldMaterialsScreen.detailMessageTop(false, true));
		assertTrue(BiomeWorldMaterialsScreen.detailRuleTop(false, true, 2) - 10
				>= BiomeWorldMaterialsScreen.detailMessageTop(false, true) + 20,
				"the Placement Rules label must clear both warning lines");
		assertEquals(189, BiomeWorldMaterialsScreen.controlsTop(265,
				BiomeWorldMaterialsScreen.detailRuleTop(false, true, 2)),
				"validation errors must reduce the rule list rather than push footer controls down");
		assertTrue(BiomeWorldMaterialsScreen.controlsTop(265,
				BiomeWorldMaterialsScreen.detailRuleTop(false, true, 2)) + 44 < 265 - 28,
				"detail controls must remain above Done at the tested window height");
		assertTrue(BiomeWorldMaterialsScreen.detailRuleTop(false, false) - 10
				>= BiomeWorldMaterialsScreen.detailTop(false) + 59,
				"the Placement Rules label must clear the replacement button");
		String source = screenSource("BiomeWorldMaterialsScreen.java");
		assertTrue(source.contains("TWO_PANE_MINIMUM = 400"));
		assertTrue(source.contains("new CompactScrollList(this, left, CONTENT_TOP"));
		assertTrue(source.contains("if (compact) compactDetail = true"));
		assertTrue(source.contains("button.orespawn.show_all"));
		assertTrue(source.contains("button.orespawn.biome.hide_routine"));
		assertTrue(source.contains("button.orespawn.dimension_materials_named"));
		assertTrue(source.contains("materialsButtonY(height), leftWidth, 20"),
				"dimension materials belong to the dimension/list pane");
		assertTrue(source.contains("tooltip.orespawn.biome.dimension"));
		assertTrue(source.contains("value.orespawn.dimension.the_nether"));
		assertTrue(source.contains("\"< \" + dimensionName(dimension).getUnformattedText() + \" >\""));
		assertTrue(source.contains("button.orespawn.biome.replace_new_terrain"));
		assertTrue(source.contains("button.orespawn.biome.leave_original"));
		assertTrue(source.contains("tooltip.orespawn.biome.no_retrogen"));
		assertTrue(source.contains("detailMessageLines"));
		assertFalse(source.contains("warning.orespawn.biome.target_not_declared"),
				"a working replacement must not display a provider-ownership warning");
		assertFalse(source.contains("label.orespawn.biome.new_terrain_only"),
				"the replacement status already explains that the change affects new terrain");
		assertFalse(source.contains("height - 39, 0xFF5555"),
				"replacement feedback must not be drawn behind the footer controls");
		assertTrue(source.contains("new BiomePlacementScreen"));
		assertTrue(source.contains("new BiomePaletteScreen"));
		assertTrue(source.contains("new DimensionMaterialsScreen"));
		assertTrue(source.contains("new GeomeBiomeScreen"));
		String materials = screenSource("DimensionMaterialsScreen.java");
		assertTrue(materials.contains("label.orespawn.dimension_materials.scope"));
		assertTrue(materials.contains("BiomeWorldMaterialsScreen.dimensionName(dimension)"));
	}

	@Test
	void paletteSettingsExposeEveryExistingPaletteControl() throws Exception {
		String settings = screenSource("BiomePaletteSettingsScreen.java");
		for (String field : new String[] { "enabled", "mode", "scope", "region_size",
				"coverage", "fallback_weight", "include_namespaces", "exclude_namespaces" }) {
			assertTrue(settings.contains("\"" + field + "\""), field);
		}
		String palettes = screenSource("BiomePaletteScreen.java");
		assertTrue(palettes.contains("session.biomeDirectory().palettes(dimension)"));
		assertTrue(palettes.contains("resetBiomePalette"));
		assertTrue(palettes.contains("resetBiomeDimension"));
		assertTrue(palettes.contains("resetAllBiomeManagement"));
		assertFalse(palettes.contains("button.orespawn.add_biome"));
	}

	private static String screenSource(String name) throws Exception {
		Path screen = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", name);
		return new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
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
