package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;
import net.minecraft.init.Fluids;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.RandomFeatureWithConfigConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge25TestBootstrap.registerVanilla();
	}

	@AfterEach
	void resetProviderRocks() {
		VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
	}

	@Test
	void configuredProviderRocksExtendForgeNativeRockRecognition() {
		VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));

		assertTrue(VanillaSpringCompatibility.isHost(Blocks.DIAMOND_BLOCK));
		assertFalse(VanillaSpringCompatibility.isHost(Blocks.DIRT));
		VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
		assertFalse(VanillaSpringCompatibility.isHost(Blocks.DIAMOND_BLOCK));
	}

	@Test
	void rewritesOnlyTheSpringLeafAndPreservesFluidAndPlacement() {
		CompositeFeature<?, ?> original = Biome.createCompositeFeature(Feature.LIQUIDS,
				new LiquidsConfig(Fluids.WATER), Biome.PASSTHROUGH,
				IPlacementConfig.NO_PLACEMENT_CONFIG);
		List<CompositeFeature<?, ?>> features = new ArrayList<>();
		features.add(original);

		assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features));
		CompositeFeature<?, ?> rewritten = features.get(0);
		assertNotSame(original, rewritten);
		assertSame(VanillaSpringCompatibility.FEATURE, rewritten.getFeature());
		assertSame(Fluids.WATER,
				((LiquidsConfig) ConfiguredFeatureInspector.featureConfig(rewritten)).field_202459_a);
		assertSame(ConfiguredFeatureInspector.basePlacement(original),
				ConfiguredFeatureInspector.basePlacement(rewritten));
		assertSame(ConfiguredFeatureInspector.placementConfig(original),
				ConfiguredFeatureInspector.placementConfig(rewritten));
		assertFalse(VanillaSpringCompatibility.rewriteFeatureList(features),
				"already rewritten leaves remain stable");
	}

	@Test
	void traversesNestedRandomFeatureGraphsWithoutChangingOtherLeaves() {
		RandomFeatureWithConfigConfig random = new RandomFeatureWithConfigConfig(
				new Feature<?>[] { Feature.LIQUIDS, Feature.ICE_AND_SNOW },
				new IFeatureConfig[] { new LiquidsConfig(Fluids.LAVA), new NoFeatureConfig() });
		CompositeFeature<?, ?> root = Biome.createCompositeFeature(
				Feature.RANDOM_FEATURE_WITH_CONFIG, random, Biome.PASSTHROUGH,
				IPlacementConfig.NO_PLACEMENT_CONFIG);
		List<CompositeFeature<?, ?>> features = new ArrayList<>();
		features.add(root);

		assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features));
		RandomFeatureWithConfigConfig rewritten = (RandomFeatureWithConfigConfig)
				ConfiguredFeatureInspector.featureConfig(features.get(0));
		assertSame(VanillaSpringCompatibility.FEATURE, rewritten.features[0]);
		assertSame(Feature.ICE_AND_SNOW, rewritten.features[1]);
		assertSame(Fluids.LAVA, ((LiquidsConfig) rewritten.configs[0]).field_202459_a);
		assertEquals(2, rewritten.features.length);
	}
}
