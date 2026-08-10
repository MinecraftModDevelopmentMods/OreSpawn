package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge14TestBootstrap.registerVanilla();
	}

	@AfterEach
	void resetProviderRocks() {
		VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
	}

	@Test
	void configuredProviderRocksExtendNativeRockRecognitionWithoutBroadTags() {
		VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));
		assertTrue(VanillaSpringCompatibility.isProviderRock(Blocks.DIAMOND_BLOCK));
		assertFalse(VanillaSpringCompatibility.isProviderRock(Blocks.DIRT));
	}

	@Test
	void nativeForgeOreReplacementPredicateRemainsThePrimaryCheck() throws Exception {
		String source = source();
		assertTrue(source.contains("isReplaceableOreGen"));
		assertTrue(source.indexOf("isReplaceableOreGen") < source.lastIndexOf("providerRocks.contains"),
				"provider rock identity must only extend Forge's native host decision");
	}

	@Test
	void compatibilityDoesNotRewriteStaticBiomeFeatureGraphs() throws Exception {
		String source = source();
		assertFalse(source.contains("getSpawnableList"));
		assertFalse(source.contains("getFeatures"));
		assertFalse(source.contains("BiomeDecorator"));
	}

	@Test
	void managedWorldgenWritesSuppressObserverDrivenChunkPopulation() throws Exception {
		String oreGeneration = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"zone", "moddev", "mc", "orespawn", "worldgen", "OreSpawnOreGeneration.java")),
				StandardCharsets.UTF_8);
		String legacyFeatures = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"com", "mcmoddev", "orespawn", "api", "FeatureBase.java")), StandardCharsets.UTF_8);
		assertTrue(oreGeneration.contains("GENERATION_WRITE_FLAGS = 2 | 16"));
		assertTrue(oreGeneration.contains("setBlockState(cursor, output, GENERATION_WRITE_FLAGS)"));
		assertTrue(legacyFeatures.contains("GENERATION_WRITE_FLAGS = 2 | 16"));
		assertTrue(legacyFeatures.contains("setBlockState(pos, output, GENERATION_WRITE_FLAGS)"));
		assertTrue(legacyFeatures.contains("setBlockState(pos, ore, GENERATION_WRITE_FLAGS)"));
	}

	private static String source() throws java.io.IOException {
		return new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "worldgen", "VanillaSpringCompatibility.java")), StandardCharsets.UTF_8);
	}
}
