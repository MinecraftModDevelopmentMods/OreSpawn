package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

import org.junit.jupiter.api.Test;
import com.mojang.serialization.Lifecycle;

class VanillaOreFeatureGateTest {

	@Test
	void nativeFamiliesRejectMixedUnknownAndEmptyOutputs() throws Exception {
		assertSame(Blocks.COPPER_ORE, nativeOutput(ore(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE)));
		assertSame(Blocks.COPPER_ORE, nativeOutput(ore(Blocks.DEEPSLATE_COPPER_ORE)));
		assertSame(Blocks.EMERALD_ORE, nativeOutput(ore(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE)));
		assertNull(nativeOutput(ore(Blocks.COPPER_ORE, Blocks.DIAMOND_ORE)));
		assertNull(nativeOutput(ore(Blocks.COPPER_ORE, Blocks.STONE)));
		assertNull(nativeOutput(ore(Blocks.STONE)));
		assertNull(nativeOutput(ore()));
		assertSame(Blocks.ANCIENT_DEBRIS, nativeOutput(new ScatteredOreFeature(
				List.of(new BlockReplacement(new BlockMatchTest(Blocks.NETHERRACK),
						Blocks.ANCIENT_DEBRIS.defaultBlockState())), 3, 1.0F)));
	}

	@Test
	void inlineQuartzKeepsItsListPositionSharedIdentityAndCachedFamily() throws Exception {
		PlacedFeature original = placed(Blocks.NETHER_QUARTZ_ORE);
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(Holder.direct(original)));
		List<Holder<PlacedFeature>> second = new ArrayList<>(List.of(Holder.direct(original)));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(first));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(second));
		assertSame(first.get(0), second.get(0));
		assertSame(original.placement(), first.get(0).value().placement());
		Feature wrapper = first.get(0).value().feature().value();
		var field = wrapper.getClass().getDeclaredField("nativeOutput");
		field.setAccessible(true);
		assertSame(Blocks.NETHER_QUARTZ_ORE, field.get(wrapper));
		assertFalse(VanillaOreFeatureGate.wrapFeatureList(first));
		clearGates();
		List<Holder<PlacedFeature>> nextWorld = new ArrayList<>(List.of(Holder.direct(original)));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(nextWorld));
		assertNotSame(first.get(0), nextWorld.get(0));
		clearGates();
	}

	@Test
	void realFeatureSorterKeepsIndicesAcrossBiomeLists() throws Exception {
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(Holder.direct(placed(Blocks.STONE)),
				Holder.direct(placed(Blocks.NETHER_QUARTZ_ORE)), Holder.direct(placed(Blocks.DIAMOND_BLOCK))));
		List<Holder<PlacedFeature>> second = new ArrayList<>(first.subList(1, 3));
		List<PlacedFeature> originals = first.stream().map(Holder::value).toList();
		var before = sort(List.of(first, second));
		VanillaOreFeatureGate.wrapFeatureList(first);
		VanillaOreFeatureGate.wrapFeatureList(second);
		var after = sort(List.of(first, second));
		assertEquals(before.get(0).features().size(), after.get(0).features().size());
		for (int i = 0; i < first.size(); i++) {
			assertEquals(before.get(0).indexMapping().applyAsInt(originals.get(i)),
					after.get(0).indexMapping().applyAsInt(first.get(i).value()));
		}
		assertSame(first.get(1), second.get(0));
		clearGates();
	}

	private static List<FeatureSorter.StepFeatureData> sort(List<List<Holder<PlacedFeature>>> lists) {
		return FeatureSorter.buildFeaturesPerStep(lists,
				features -> List.of(HolderSet.direct(features)), true);
	}

	private static Block nativeOutput(Feature feature) throws Exception {
		Method method = VanillaOreFeatureGate.class.getDeclaredMethod("nativeOutput", Feature.class);
		method.setAccessible(true);
		return (Block) method.invoke(null, feature);
	}

	@Test
	void registeredWrappersReleaseThePreviousServersFeature() throws Exception {
		clearGates();
		Holder<PlacedFeature> firstOriginal = registered("minecraft:ore_quartz_nether", placed(Blocks.NETHER_QUARTZ_ORE));
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(firstOriginal));
		VanillaOreFeatureGate.wrapFeatureList(first);
		clearGates();
		Holder<PlacedFeature> nextOriginal = registered("minecraft:ore_quartz_nether", placed(Blocks.NETHER_QUARTZ_ORE));
		List<Holder<PlacedFeature>> next = new ArrayList<>(List.of(nextOriginal));
		VanillaOreFeatureGate.wrapFeatureList(next);
		assertNotSame(first.get(0), next.get(0));
		Feature wrapper = next.get(0).value().feature().value();
		var delegate = wrapper.getClass().getDeclaredField("original");
		delegate.setAccessible(true);
		assertSame(nextOriginal.value().feature(), delegate.get(wrapper));
		clearGates();
	}

	@Test
	void matchingStoneWrappersAreSharedOnlyWithinOneServerLifetime() {
		StoneReplacer.clearMatchingStoneGates();
		Holder<PlacedFeature> original = registered("minecraft:ore_granite_upper", placed(Blocks.GRANITE));
		List<Holder<PlacedFeature>> first = new ArrayList<>(List.of(original));
		List<Holder<PlacedFeature>> second = new ArrayList<>(List.of(original));
		StoneReplacer.wrapVanillaMatchingStoneFeatures(first);
		StoneReplacer.wrapVanillaMatchingStoneFeatures(second);
		assertSame(first.get(0), second.get(0));
		StoneReplacer.clearMatchingStoneGates();
		List<Holder<PlacedFeature>> next = new ArrayList<>(List.of(original));
		StoneReplacer.wrapVanillaMatchingStoneFeatures(next);
		assertNotSame(first.get(0), next.get(0));
		StoneReplacer.clearMatchingStoneGates();
	}

	@Test
	void customOreSubclassesAreNotMistakenForVanillaGeneration() throws Exception {
		OreFeature custom = new OreFeature(new BlockMatchTest(Blocks.STONE),
				Blocks.DIAMOND_ORE.defaultBlockState(), 8) { };
		assertNull(nativeOutput(custom));
	}

	@Test
	void stoppingAWorldRebuildsGlobalGeologyWithoutItsBiomeRegistry() throws Exception {
		MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
		Biome biome = new Biome.BiomeBuilder().hasPrecipitation(false).temperature(0.5F).downfall(0.5F)
				.specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
				.mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
		registry.register(Biomes.PLAINS, biome, RegistrationInfo.BUILT_IN);
		RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(registry.freeze()));
		var weights = BakedGeomeConfig.class.getDeclaredField("biomeWeights");
		weights.setAccessible(true);
		try {
			BiomeRegistryAccess.bind(access);
			GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false));
			assertFalse(((java.util.Map<?, ?>) weights.get(GeomeConfig.baked())).isEmpty());
			WorldGeologyProfileManager.clearStoppedWorldState(access);
			assertFalse(BiomeRegistryAccess.isBound());
			assertTrue(((java.util.Map<?, ?>) weights.get(GeomeConfig.baked())).isEmpty());
		} finally {
			WorldGeologyProfileManager.clearStoppedWorldState(access);
		}
	}

	private static Holder<PlacedFeature> registered(String id, PlacedFeature feature) {
		MappedRegistry<PlacedFeature> registry = new MappedRegistry<>(Registries.PLACED_FEATURE, Lifecycle.stable());
		return registry.register(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.parse(id)),
				feature, RegistrationInfo.BUILT_IN);
	}

	private static void clearGates() throws Exception {
		Method method = VanillaOreFeatureGate.class.getDeclaredMethod("clearGates");
		method.setAccessible(true);
		method.invoke(null);
	}

	private static PlacedFeature placed(Block block) {
		return new PlacedFeature(Holder.direct(ore(block)), List.of());
	}

	private static OreFeature ore(Block... outputs) {
		List<BlockReplacement> targets = new ArrayList<>();
		for (Block output : outputs) targets.add(new BlockReplacement(
				new BlockMatchTest(Blocks.NETHERRACK), output.defaultBlockState()));
		return new OreFeature(targets, 14);
	}
}
