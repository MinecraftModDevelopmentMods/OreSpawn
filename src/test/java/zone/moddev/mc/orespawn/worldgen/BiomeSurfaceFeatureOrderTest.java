package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeSurfaceFeatureOrderTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.bootStrap();
	}

	@Test
	void eventAndRuntimeInstallersKeepSurfacesEarlyAndBedrockLast() {
		BiomeSurfaceFeature.registerConfiguredFeature();
		FlatBedrockFeature.registerConfiguredFeature();
		BiomeGenerationSettingsBuilder generation = new BiomeGenerationSettingsBuilder(
				net.minecraft.world.biome.BiomeGenerationSettings.EMPTY);

		assertTrue(BiomeSurfaceFeature.install(generation));

		ConfiguredFeature<?, ?> surfaces = BiomeSurfaceFeature.configuredFeature();
		ConfiguredFeature<?, ?> bedrock = FlatBedrockFeature.configuredFeature();
		List<Supplier<ConfiguredFeature<?, ?>>> local =
				generation.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS);
		List<Supplier<ConfiguredFeature<?, ?>>> top =
				generation.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION);
		assertTrue(local.stream().anyMatch(feature -> feature.get() == surfaces));
		assertFalse(top.stream().anyMatch(feature -> feature.get() == surfaces));

		List<List<Supplier<ConfiguredFeature<?, ?>>>> runtime = new ArrayList<>();
		assertTrue(BiomeFeatureInstaller.installSurfaceStages(runtime));
		List<Supplier<ConfiguredFeature<?, ?>>> runtimeLocal = step(runtime,
				GenerationStage.Decoration.LOCAL_MODIFICATIONS);
		List<Supplier<ConfiguredFeature<?, ?>>> runtimeTop = step(runtime,
				GenerationStage.Decoration.TOP_LAYER_MODIFICATION);
		assertTrue(runtimeLocal.stream().anyMatch(feature -> feature.get() == surfaces));
		assertFalse(runtimeLocal.stream().anyMatch(feature -> feature.get() == bedrock));
		assertTrue(runtimeTop.stream().anyMatch(feature -> feature.get() == bedrock));
		assertFalse(runtimeTop.stream().anyMatch(feature -> feature.get() == surfaces));
	}

	private static List<Supplier<ConfiguredFeature<?, ?>>> step(
			List<List<Supplier<ConfiguredFeature<?, ?>>>> features, GenerationStage.Decoration step) {
		while (features.size() <= step.ordinal()) features.add(new ArrayList<>());
		return features.get(step.ordinal());
	}
}
