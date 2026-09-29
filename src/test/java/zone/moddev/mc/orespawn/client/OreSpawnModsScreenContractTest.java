package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

class OreSpawnModsScreenContractTest {
	private static final Path CLIENT = Paths.get("src", "main", "java", "zone", "moddev", "mc",
			"orespawn", "client");

	@Test
	void packagedCogIsARealSixteenPixelPngRatherThanAFontGlyph() throws Exception {
		Path cog = Paths.get("src", "main", "resources", "assets", "orespawn", "textures", "gui",
				"cog.png");
		byte[] bytes = Files.readAllBytes(cog);
		assertArrayEquals(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47},
				java.util.Arrays.copyOf(bytes, 4));
		BufferedImage image = ImageIO.read(cog.toFile());
		assertEquals(16, image.getWidth());
		assertEquals(16, image.getHeight());
		String source = read(CLIENT.resolve("CogButton.java"));
		assertTrue(source.contains("textures/gui/cog.png"));
		assertFalse(source.contains("\\u2699"));
		assertTrue(source.contains("GlStateManager.scale(1.5F, 1.5F, 1.0F)"));
		assertTrue(source.contains("if (enabled)"));
	}

	@Test
	void directoryOwnsPaginationClippingTooltipsAndFailureContainment() throws Exception {
		String source = read(CLIENT.resolve("OreSpawnModsScreen.java"));
		assertTrue(source.contains("pageCount(entries.size(), pageSize)"));
		assertTrue(source.contains("font.trimStringToWidth"));
		assertTrue(source.contains("cog.enabled = entry.configurable()"));
		assertTrue(source.contains("tooltip.orespawn.mod.unavailable"));
		assertTrue(source.contains("entry.extension.createScreen(this)"));
		assertTrue(source.contains("if (screen == null)"));
		assertTrue(source.contains("catch (RuntimeException failure)"));
		assertTrue(source.contains("minecraft.displayGuiScreen(parent)"));
		assertTrue(source.contains("WorldSettingsExtensionNavigation.open(this, screen)"));
		String navigation = read(CLIENT.resolve("WorldSettingsExtensionNavigation.java"));
		assertTrue(navigation.contains("event.getGui() == null"));
		assertTrue(navigation.contains("event.getGui() instanceof GuiMainMenu"));
		assertTrue(navigation.contains("event.setGui(directory)"));
	}

	@Test
	void discoveryConsumesRecordedBoundariesRatherThanScanningProfilesInTheGui() throws Exception {
		String model = read(CLIENT.resolve("OreSpawnModDirectoryModel.java"));
		String legacyThree = read(Paths.get("src", "main", "java", "com", "mcmoddev", "orespawn",
				"compat", "LegacyOs3Bridge.java"));
		String legacyTwo = read(Paths.get("src", "main", "java", "zone", "moddev", "mc", "orespawn",
				"worldgen", "LegacyConfigMigrator.java"));
		assertTrue(model.contains("Loader.instance().getActiveModList()"));
		assertTrue(model.contains("mod.getName()"));
		assertTrue(model.contains("mod.getVersion()"));
		assertFalse(model.contains("Files."));
		assertFalse(model.contains("JsonParser"));
		assertTrue(legacyThree.contains("recordLegacyLineage(owner, 1)"));
		assertTrue(legacyThree.contains("recordLegacyLineage(owner, 3)"));
		assertTrue(legacyTwo.contains("recordLegacyLineage(owner, 2)"));
	}

	private static String read(Path path) throws Exception {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}
}
