package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.util.text.TextComponentTranslation;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

/** Protects Minecraft 1.10's pre-flattening block-name translation contract. */
class LegacyBlockTranslationTest {
	private static final List<String> BLOCK_LABEL_SOURCES = Arrays.asList(
			"FluidBlockPickerScreen.java",
			"FluidDepositEntryScreen.java",
			"FluidDepositListScreen.java",
			"OreSpawnWorldSettingsScreen.java");

	@BeforeAll
	static void bootstrapVanilla() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void everyBlockLabelUsesTheLegacyNameKey() throws Exception {
		Path clientDir = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client");
		List<String> unsuffixed = new ArrayList<>();
		for (String sourceName : BLOCK_LABEL_SOURCES) {
			String source = new String(Files.readAllBytes(clientDir.resolve(sourceName)),
					StandardCharsets.UTF_8);
			if (source.contains("new TextComponentTranslation(block.getTranslationKey())")) {
				unsuffixed.add(sourceName);
			}
		}

		assertEquals(java.util.Collections.emptyList(), unsuffixed,
				"Minecraft 1.10 block labels require getTranslationKey() + \".name\"");
	}

	@Test
	void vanillaFluidBlocksResolveThroughLegacyNameKeys() {
		TextComponentTranslation lava = DialogTexts.blockName(Blocks.LAVA);
		TextComponentTranslation water = DialogTexts.blockName(Blocks.WATER);

		assertEquals("tile.lava.name", lava.getKey());
		assertEquals("tile.water.name", water.getKey());
		assertEquals("missing:fluid", DialogTexts.blockName(null, "missing:fluid").getUnformattedText());
	}
}
