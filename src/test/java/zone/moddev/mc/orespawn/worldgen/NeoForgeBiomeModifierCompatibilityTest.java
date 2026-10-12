package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier.Phase;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NeoForgeBiomeModifierCompatibilityTest {

	@BeforeAll
	static void registerFeatures() {
		StoneReplacer.registerConfiguredFeature();
		BiomeSurfaceFeature.registerConfiguredFeature();
		FlatBedrockFeature.registerConfiguredFeature();
		OreSpawnOreGeneration.registerConfiguredFeatures();
		FluidDepositFeature.registerConfiguredFeature();
	}

	@Test
	void bothLoaderSignaturesInstallTheSameFeaturesWithoutDuplicates() throws Exception {
		BiomeInfo.Builder legacy = builder();
		BiomeInfo.Builder current = builder();
		OreSpawnBiomeModifier.INSTANCE.modify(null, Phase.ADD, legacy);
		Method entryPoint = currentEntryPoint();
		entryPoint.invoke(OreSpawnBiomeModifier.INSTANCE, RegistryAccess.EMPTY, null, Phase.ADD, current);

		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			assertEquals(legacy.getGenerationSettings().getFeatures(step),
					current.getGenerationSettings().getFeatures(step), step.name());
		}
		var local = current.getGenerationSettings().getFeatures(GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		assertEquals(2, local.size());
		assertTrue(local.get(0).value() == StoneReplacer.placedFeature().value());
		assertTrue(local.get(1).value() == BiomeSurfaceFeature.placedFeature().value());

		// A loader calls one signature, but accidental repeat calls must not duplicate decoration work.
		entryPoint.invoke(OreSpawnBiomeModifier.INSTANCE, RegistryAccess.EMPTY, null, Phase.ADD, current);
		OreSpawnBiomeModifier.INSTANCE.modify(null, Phase.ADD, current);
		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			assertEquals(legacy.getGenerationSettings().getFeatures(step),
					current.getGenerationSettings().getFeatures(step), step.name());
		}
	}

	@Test
	void bothLoaderSignaturesIgnoreEveryOtherPhase() throws Exception {
		Method entryPoint = currentEntryPoint();
		BiomeInfo.Builder legacy = builder();
		BiomeInfo.Builder current = builder();
		for (Phase phase : Phase.values()) {
			if (phase == Phase.ADD) continue;
			OreSpawnBiomeModifier.INSTANCE.modify(null, phase, legacy);
			entryPoint.invoke(OreSpawnBiomeModifier.INSTANCE, RegistryAccess.EMPTY, null, phase, current);
		}
		for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
			assertTrue(legacy.getGenerationSettings().getFeatures(step).isEmpty(), step.name());
			assertTrue(current.getGenerationSettings().getFeatures(step).isEmpty(), step.name());
		}
	}

	private static Method currentEntryPoint() throws Exception {
		// Compile against the oldest supported loader and check the newer descriptor on the same class.
		return OreSpawnBiomeModifier.class.getMethod("modify", RegistryAccess.class,
				Holder.class, Phase.class, BiomeInfo.Builder.class);
	}

	private static BiomeInfo.Builder builder() {
		Biome biome = new Biome.BiomeBuilder().hasPrecipitation(false)
				.temperature(0.5F).downfall(0.5F)
				.specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
				.mobSpawnSettings(MobSpawnSettings.EMPTY)
				.generationSettings(BiomeGenerationSettings.EMPTY).build();
		return BiomeInfo.Builder.copyOf(Biomes.PLAINS, new BiomeInfo(
				biome.getModifiedClimateSettings(), biome.getAttributes(),
				biome.getModifiedSpecialEffects(), biome.getGenerationSettings()));
	}
}
