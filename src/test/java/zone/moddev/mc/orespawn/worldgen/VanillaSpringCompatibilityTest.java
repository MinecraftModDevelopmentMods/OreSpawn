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

import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.SingleRandomFeature;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Bootstrap.register();
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
	void rewritesOnlyTheSpringLeafAndPreservesFluidAndDecorator() {
		ConfiguredFeature<?> original = Biome.createDecoratedFeature(Feature.SPRING_FEATURE,
				new LiquidsConfig(Fluids.WATER.getDefaultState()), Placement.NOPE,
				new NoPlacementConfig());
		List<ConfiguredFeature<?>> features = new ArrayList<>();
		features.add(original);

		assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features));
		ConfiguredFeature<?> rewritten = features.get(0);
		assertNotSame(original, rewritten);
		DecoratedFeatureConfig before = (DecoratedFeatureConfig) original.config;
		DecoratedFeatureConfig after = (DecoratedFeatureConfig) rewritten.config;
		assertSame(before.decorator, after.decorator);
		assertSame(VanillaSpringCompatibility.FEATURE, after.feature.feature);
		assertSame(((LiquidsConfig) before.feature.config).state,
				((LiquidsConfig) after.feature.config).state);
		assertFalse(VanillaSpringCompatibility.rewriteFeatureList(features),
				"already rewritten leaves remain stable");
	}

	@Test
	void traversesNestedRandomFeatureGraphsWithoutChangingOtherLeaves() {
		ConfiguredFeature<?> spring = Biome.createDecoratedFeature(Feature.SPRING_FEATURE,
				new LiquidsConfig(Fluids.LAVA.getDefaultState()), Placement.NOPE,
				new NoPlacementConfig());
		ConfiguredFeature<?> untouched = new ConfiguredFeature<>(Feature.FREEZE_TOP_LAYER,
				new NoFeatureConfig());
		ConfiguredFeature<?> random = new ConfiguredFeature<>(Feature.SIMPLE_RANDOM_SELECTOR,
				new SingleRandomFeature(java.util.Arrays.asList(spring, untouched)));
		List<ConfiguredFeature<?>> features = new ArrayList<>();
		features.add(random);

		assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features));
		SingleRandomFeature rewritten = (SingleRandomFeature) features.get(0).config;
		DecoratedFeatureConfig decorated = (DecoratedFeatureConfig) rewritten.features.get(0).config;
		assertSame(VanillaSpringCompatibility.FEATURE, decorated.feature.feature);
		assertSame(untouched, rewritten.features.get(1));
		assertEquals(2, rewritten.features.size());
	}
}
