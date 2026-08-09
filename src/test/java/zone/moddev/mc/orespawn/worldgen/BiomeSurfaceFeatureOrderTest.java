package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeSurfaceFeatureOrderTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.register();
	}

	@Test
	void staticInstallerKeepsSurfacesEarlyAndBedrockLast() throws ReflectiveOperationException {
		setConfiguredFeature(StoneReplacer.class, StoneReplacer.FEATURE);
		setConfiguredFeature(OreSpawnOreGeneration.class, OreSpawnOreGeneration.FEATURE);
		setConfiguredFeature(FluidDepositFeature.class, FluidDepositFeature.FEATURE);
		setConfiguredFeature(BiomeSurfaceFeature.class, BiomeSurfaceFeature.FEATURE);
		setConfiguredFeature(FlatBedrockFeature.class, FlatBedrockFeature.FEATURE);
		Biome biome = new TestBiome(new Biome.Builder()
				.precipitation(Biome.RainType.NONE).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(0.7F).downfall(0.8F)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(SurfaceBuilder.DEFAULT, SurfaceBuilder.GRASS_DIRT_GRAVEL_CONFIG));

		BiomeFeatureInstaller.installFeatures(biome, true, true, true, true, true, true);
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(StoneReplacer.configuredFeature()));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(OreSpawnOreGeneration.configuredFeature()));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(FluidDepositFeature.configuredFeature()));
		ConfiguredFeature<?, ?> surfaces = BiomeSurfaceFeature.configuredFeature();
		ConfiguredFeature<?, ?> bedrock = FlatBedrockFeature.configuredFeature();
		assertTrue(biome.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(surfaces));
		assertFalse(biome.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(surfaces));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(bedrock));
		assertFalse(biome.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(bedrock));

		Biome inactive = new TestBiome(new Biome.Builder()
				.precipitation(Biome.RainType.NONE).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(0.7F).downfall(0.8F)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(SurfaceBuilder.DEFAULT, SurfaceBuilder.GRASS_DIRT_GRAVEL_CONFIG));
		BiomeFeatureInstaller.installFeatures(inactive, false, false, false, false, false, false);
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(StoneReplacer.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(OreSpawnOreGeneration.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(FluidDepositFeature.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(surfaces));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(bedrock));
	}

	private static void setConfiguredFeature(Class<?> owner, Feature<NoFeatureConfig> feature)
			throws ReflectiveOperationException {
		java.lang.reflect.Field field = owner.getDeclaredField("configuredFeature");
		field.setAccessible(true);
		field.set(null, feature.withConfiguration(new NoFeatureConfig())
				.withPlacement(Placement.NOPE.configure(new NoPlacementConfig())));
	}

	private static final class TestBiome extends Biome {
		TestBiome(Biome.Builder builder) { super(builder); }
	}
}
