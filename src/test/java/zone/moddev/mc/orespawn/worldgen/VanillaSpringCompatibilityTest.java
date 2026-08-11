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
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
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
	void springChecksEveryNeighbourIsLoadedBeforeReadingItsState() throws Exception {
		String source = source();
		int loadedGuard = source.indexOf("if (!loaded(world, pos)");
		int firstRead = source.indexOf("world.getBlockState(pos.up())");
		assertTrue(loadedGuard >= 0, "spring generation must have an explicit loaded-neighbour guard");
		assertTrue(firstRead > loadedGuard,
				"spring generation must reject unloaded chunk edges before any adjacent block read");
		assertTrue(source.contains("loaded(world, pos.west())"));
		assertTrue(source.contains("loaded(world, pos.east())"));
		assertTrue(source.contains("loaded(world, pos.north())"));
		assertTrue(source.contains("loaded(world, pos.south())"));
	}

	@Test
	void managedWorldgenWritesUseForge110NoNeighbourUpdateFlag() throws Exception {
		String oreGeneration = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"zone", "moddev", "mc", "orespawn", "worldgen", "OreSpawnOreGeneration.java")),
				StandardCharsets.UTF_8);
		String legacyFeatures = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"com", "mcmoddev", "orespawn", "api", "FeatureBase.java")), StandardCharsets.UTF_8);
		assertTrue(oreGeneration.contains("GENERATION_WRITE_FLAGS = 2;"));
		assertFalse(oreGeneration.contains("GENERATION_WRITE_FLAGS = 2 | 16"));
		assertTrue(oreGeneration.contains("setBlockState(cursor.toImmutable(), output, GENERATION_WRITE_FLAGS)"));
		assertTrue(legacyFeatures.contains("GENERATION_WRITE_FLAGS = 2;"));
		assertFalse(legacyFeatures.contains("GENERATION_WRITE_FLAGS = 2 | 16"));
		assertTrue(legacyFeatures.contains("setBlockState(immutable(pos), output, GENERATION_WRITE_FLAGS)"));
		assertTrue(legacyFeatures.contains("setBlockState(immutable(pos), ore, GENERATION_WRITE_FLAGS)"));
		assertTrue(legacyFeatures.contains("((BlockPos.MutableBlockPos) pos).toImmutable()"));
	}

	private static String source() throws java.io.IOException {
		return new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "worldgen", "VanillaSpringCompatibility.java")), StandardCharsets.UTF_8);
	}
}
