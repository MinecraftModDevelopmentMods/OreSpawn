package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeSurfaceFeatureOrderTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void eventAndRuntimeInstallersKeepSurfacesEarlyAndBedrockLast() {
		StoneReplacer.registerConfiguredFeature();
		BiomeSurfaceFeature.registerConfiguredFeature();
		FlatBedrockFeature.registerConfiguredFeature();
		BiomeGenerationSettingsBuilder generation = new BiomeGenerationSettingsBuilder(
				net.minecraft.world.level.biome.BiomeGenerationSettings.EMPTY);

		assertTrue(StoneReplacer.install(generation));
		assertTrue(BiomeSurfaceFeature.install(generation));

		Holder<PlacedFeature> surfaces = BiomeSurfaceFeature.placedFeature();
		Holder<PlacedFeature> bedrock = FlatBedrockFeature.placedFeature();
		var local = generation.getFeatures(GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		var top = generation.getFeatures(GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		assertTrue(local.size() >= 2);
		assertTrue(local.get(0).value() == StoneReplacer.placedFeature().value());
		assertTrue(local.get(1).value() == surfaces.value());
		assertFalse(top.stream().anyMatch(feature -> feature.value() == surfaces.value()));

		List<List<Holder<PlacedFeature>>> runtime = new ArrayList<>();
		assertTrue(BiomeFeatureInstaller.installSurfaceStages(runtime, true, true));
		List<Holder<PlacedFeature>> runtimeLocal = step(runtime,
				GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		List<Holder<PlacedFeature>> runtimeTop = step(runtime,
				GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		assertTrue(runtimeLocal.size() >= 2);
		assertTrue(runtimeLocal.get(0).value() == StoneReplacer.placedFeature().value());
		assertTrue(runtimeLocal.get(1).value() == surfaces.value());
		assertFalse(runtimeLocal.stream().anyMatch(feature -> feature.value() == bedrock.value()));
		assertTrue(runtimeTop.stream().anyMatch(feature -> feature.value() == bedrock.value()));
		assertFalse(runtimeTop.stream().anyMatch(feature -> feature.value() == surfaces.value()));
	}

	private static List<Holder<PlacedFeature>> step(
			List<List<Holder<PlacedFeature>>> features, GenerationStep.Decoration step) {
		while (features.size() <= step.ordinal()) features.add(new ArrayList<>());
		return features.get(step.ordinal());
	}
}
