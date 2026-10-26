package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.Biomes;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;

class VanillaOreFeatureGateTest {
	@BeforeAll
	static void bootstrap() { Forge36TestBootstrap.registerVanilla(); }

	@Test
	void registeredQuartzGatesReleaseTheirDynamicRegistryReferences() throws Exception {
		VanillaOreFeatureGate.clearGates();
		Holder<PlacedFeature> original = VanillaRegistries.createWorldLookup()
				.lookupOrThrow(Registries.PLACED_FEATURE).getOrThrow(net.minecraft.resources.ResourceKey.create(
						Registries.PLACED_FEATURE, net.minecraft.resources.Identifier.parse("minecraft:ore_quartz_nether")));
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(original));
		List<Holder<PlacedFeature>> second = new ArrayList<>(List.of(original));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
		assertSame(first.get(0), second.get(0));
		Object feature = first.get(0).value().feature().value();
		java.lang.reflect.Field delegate = feature.getClass().getDeclaredField("original");
		delegate.setAccessible(true);
		assertNotNull(delegate.get(feature));
		VanillaOreFeatureGate.clearGates();
		for (String name : List.of("VANILLA_GATES", "SUPPRESSIBLE_GATES")) {
			java.lang.reflect.Field cache = VanillaOreFeatureGate.class.getDeclaredField(name);
			cache.setAccessible(true);
			assertTrue(((java.util.Map<?, ?>) cache.get(null)).isEmpty());
		}
		// A locally held immutable feature stays valid; no static cache retains it.
		assertNotNull(delegate.get(feature));
		List<Holder<PlacedFeature>> nextWorld = new ArrayList<>(List.of(original));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(nextWorld));
		assertNotSame(first.get(0), nextWorld.get(0));
		VanillaOreFeatureGate.clearGates();
	}

	@Test
	void inlineQuartzKeepsItsListPositionAndSharedIdentity() {
		OreFeature quartz = ore(Blocks.NETHER_QUARTZ_ORE);
		PlacedFeature original = new PlacedFeature(Holder.direct(quartz), List.of());
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(Holder.direct(original)));
		List<Holder<PlacedFeature>> second = new ArrayList<>(List.of(Holder.direct(original)));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
		assertNotSame(original, first.get(0).value());
		assertSame(first.get(0).value(), second.get(0).value());
		assertSame(original.placement(), first.get(0).value().placement());
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(first));
		VanillaOreFeatureGate.clearGates();
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
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.COPPER_ORE, Blocks.STONE)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore(Blocks.STONE)));
		assertNull(VanillaOreFeatureGate.nativeOutput(ore()));
		Feature debris = new ScatteredOreFeature(ore(Blocks.ANCIENT_DEBRIS).targetStates(), 14, 0);
		assertSame(Blocks.ANCIENT_DEBRIS, VanillaOreFeatureGate.nativeOutput(debris));
	}

	@Test
	void realBiomeSourceSorterKeepsTheSameIndicesAcrossBiomes() throws Exception {
		Holder<Biome> plains = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
		Holder<Biome> forest = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.FOREST);
		BiomeGenerationSettings oldPlains = plains.value().getGenerationSettings();
		BiomeGenerationSettings oldForest = forest.value().getGenerationSettings();
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(
				placed(Blocks.STONE), placed(Blocks.NETHER_QUARTZ_ORE), placed(Blocks.DIRT)));
		List<Holder<PlacedFeature>> second = new ArrayList<>(first.subList(1, 3));
		List<PlacedFeature> originals = first.stream().map(Holder::value).toList();
		try {
			setFeatures(plains.value(), first);
			setFeatures(forest.value(), second);
			List<FeatureSorter.StepFeatureData> before = sort(List.of(plains, forest));
			assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
			assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
			setFeatures(plains.value(), first);
			setFeatures(forest.value(), second);
			List<FeatureSorter.StepFeatureData> after = sort(List.of(plains, forest));
			assertEquals(before.get(0).features().size(), after.get(0).features().size());
			for (int i = 0; i < first.size(); i++) {
				assertEquals(before.get(0).indexMapping().applyAsInt(originals.get(i)),
						after.get(0).indexMapping().applyAsInt(first.get(i).value()));
			}
			assertSame(first.get(1).value(), second.get(0).value());
		} finally {
			plains.value().generationSettings = oldPlains;
			forest.value().generationSettings = oldForest;
			VanillaOreFeatureGate.clearGates();
		}
	}

	private static List<FeatureSorter.StepFeatureData> sort(List<Holder<Biome>> biomes) {
		return FeatureSorter.buildFeaturesPerStep(biomes,
				biome -> biome.value().getGenerationSettings().features(), true);
	}
	private static void setFeatures(Biome biome, List<Holder<PlacedFeature>> features) {
		BiomeGenerationSettings.PlainBuilder builder = new BiomeGenerationSettings.PlainBuilder();
		features.forEach(feature -> builder.addFeature(0, feature));
		biome.generationSettings = builder.build();
	}

	private static Holder<PlacedFeature> placed(Block block) {
		return Holder.direct(new PlacedFeature(Holder.direct(ore(block)), List.of()));
	}

	private static OreFeature ore(Block... outputs) {
		List<BlockReplacement> targets = new ArrayList<>();
		for (Block output : outputs) targets.add(new BlockReplacement(
				new BlockMatchTest(Blocks.NETHERRACK), output.defaultBlockState()));
		return new OreFeature(targets, 14);
	}
}
