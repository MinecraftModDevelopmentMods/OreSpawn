package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;

class VanillaOreFeatureGateTest {
	@BeforeAll
	static void bootstrap() { Forge36TestBootstrap.registerVanilla(); }

	@Test
	void inlineQuartzKeepsItsListPositionAndSharedIdentity() {
		ConfiguredFeature<?, ?> quartz = new ConfiguredFeature<>(Feature.ORE,
				new OreConfiguration(new BlockMatchTest(Blocks.NETHERRACK),
						Blocks.NETHER_QUARTZ_ORE.defaultBlockState(), 14));
		PlacedFeature original = new PlacedFeature(Holder.direct(quartz), List.of());
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(Holder.direct(original)));
		List<Holder<PlacedFeature>> second = new ArrayList<>(List.of(Holder.direct(original)));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
		assertNotSame(original, first.get(0).value());
		assertSame(first.get(0).value(), second.get(0).value());
		assertSame(original.placement(), first.get(0).value().placement());
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(first));
		VanillaOreFeatureGate.clearInlineGates();
		List<Holder<PlacedFeature>> nextWorld = new ArrayList<>(List.of(Holder.direct(original)));
		VanillaOreFeatureGate.wrapFeatureList(nextWorld);
		assertNotSame(first.get(0).value(), nextWorld.get(0).value());
	}

	@Test
	void nativeFamiliesRejectMixedUnknownAndEmptyOutputs() {
		assertSame(Blocks.COPPER_ORE, VanillaOreFeatureGate.nativeOutput(
				ore(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)));
		assertSame(Blocks.COPPER_ORE, VanillaOreFeatureGate.nativeOutput(ore(Blocks.DEEPSLATE_COPPER_ORE)));
		assertSame(Blocks.EMERALD_ORE, VanillaOreFeatureGate.nativeOutput(
				ore(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.COPPER_ORE, Blocks.DIAMOND_ORE)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.COPPER_ORE, Blocks.BLUE_WOOL)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.BLUE_WOOL)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore()));
		ConfiguredFeature<?, ?> debris = new ConfiguredFeature<>(Feature.SCATTERED_ORE,
				ore(Blocks.ANCIENT_DEBRIS).config());
		assertSame(Blocks.ANCIENT_DEBRIS, VanillaOreFeatureGate.nativeOutput(debris));
	}

	@Test
	void realBiomeSourceSorterKeepsTheSameIndicesAcrossBiomes() throws Exception {
		Holder<Biome> plains = BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS);
		Holder<Biome> forest = BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.FOREST);
		BiomeGenerationSettings oldPlains = plains.value().getGenerationSettings();
		BiomeGenerationSettings oldForest = forest.value().getGenerationSettings();
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(
				placed(Blocks.BLUE_WOOL), placed(Blocks.NETHER_QUARTZ_ORE), placed(Blocks.GREEN_WOOL)));
		List<Holder<PlacedFeature>> second = new ArrayList<>(first.subList(1, 3));
		java.lang.reflect.Method sorter = BiomeSource.class.getDeclaredMethod(
				"buildFeaturesPerStep", List.class, boolean.class);
		sorter.setAccessible(true);
		try {
			setFeatures(plains.value(), first);
			setFeatures(forest.value(), second);
			List<BiomeSource.StepFeatureData> before = sort(sorter, plains, List.of(plains, forest));
			assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
			assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
			setFeatures(plains.value(), first);
			setFeatures(forest.value(), second);
			List<BiomeSource.StepFeatureData> after = sort(sorter, plains, List.of(plains, forest));
			assertEquals(before.get(0).features().size(), after.get(0).features().size());
			for (int i = 0; i < first.size(); i++) {
				assertEquals(i, after.get(0).indexMapping().applyAsInt(first.get(i).value()));
			}
			assertSame(first.get(1).value(), second.get(0).value());
		} finally {
			plains.value().generationSettings = oldPlains;
			forest.value().generationSettings = oldForest;
			VanillaOreFeatureGate.clearInlineGates();
		}
	}

	@SuppressWarnings("unchecked")
	private static List<BiomeSource.StepFeatureData> sort(java.lang.reflect.Method sorter,
			Holder<Biome> biome, List<Holder<Biome>> biomes) throws Exception {
		return (List<BiomeSource.StepFeatureData>) sorter.invoke(new FixedBiomeSource(biome), biomes, true);
	}

	private static void setFeatures(Biome biome, List<Holder<PlacedFeature>> features) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder();
		features.forEach(feature -> builder.addFeature(0, feature));
		biome.generationSettings = builder.build();
	}

	private static Holder<PlacedFeature> placed(Block block) {
		return Holder.direct(new PlacedFeature(Holder.direct(ore(block)), List.of()));
	}

	private static ConfiguredFeature<OreConfiguration, ?> ore(Block... outputs) {
		List<OreConfiguration.TargetBlockState> targets = new ArrayList<>();
		for (Block output : outputs) targets.add(OreConfiguration.target(
				new BlockMatchTest(Blocks.NETHERRACK), output.defaultBlockState()));
		return new ConfiguredFeature<>(Feature.ORE, new OreConfiguration(targets, 14));
	}
}
