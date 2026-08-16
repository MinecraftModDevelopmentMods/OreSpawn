package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import org.junit.jupiter.api.Test;

class BiomeSurfaceFeatureOrderTest {
	@Test
	void surfacesRunBeforeStructuresAndVegetationWhileFlatBedrockStaysLast() {
		BiomeSurfaceFeature.registerConfiguredFeature();
		FlatBedrockFeature.registerConfiguredFeature();
		BiomeGenerationSettingsBuilder generation =
				new BiomeGenerationSettingsBuilder(BiomeGenerationSettings.EMPTY);

		assertTrue(OreSpawnBiomeModifier.apply(generation));

		Holder<PlacedFeature> surfaces = BiomeSurfaceFeature.placedFeature();
		Holder<PlacedFeature> bedrock = FlatBedrockFeature.placedFeature();
		var local = generation.getFeatures(GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		var top = generation.getFeatures(GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		assertTrue(local.stream().anyMatch(feature -> feature.value() == surfaces.value()));
		assertFalse(local.stream().anyMatch(feature -> feature.value() == bedrock.value()));
		assertTrue(top.stream().anyMatch(feature -> feature.value() == bedrock.value()));
		assertFalse(top.stream().anyMatch(feature -> feature.value() == surfaces.value()));
	}
}
