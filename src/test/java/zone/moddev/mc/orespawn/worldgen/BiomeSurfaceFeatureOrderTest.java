package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import net.minecraft.world.gen.surfacebuilders.CompositeSurfaceBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BiomeSurfaceFeatureOrderTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge25TestBootstrap.registerVanilla();
	}

	@Test
	void staticInstallerKeepsSurfacesEarlyAndBedrockLast() throws Exception {
		setConfiguredFeature(StoneReplacer.class, StoneReplacer.FEATURE);
		setConfiguredFeature(OreSpawnOreGeneration.class, OreSpawnOreGeneration.FEATURE);
		setConfiguredFeature(FluidDepositFeature.class, FluidDepositFeature.FEATURE);
		setConfiguredFeature(BiomeSurfaceFeature.class, BiomeSurfaceFeature.FEATURE);
		setConfiguredFeature(FlatBedrockFeature.class, FlatBedrockFeature.FEATURE);
		Biome biome = new TestBiome(new Biome.BiomeBuilder()
				.precipitation(Biome.RainType.NONE).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(0.7F).downfall(0.8F)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(new CompositeSurfaceBuilder<>(Biome.DEFAULT_SURFACE_BUILDER,
						Biome.GRASS_DIRT_GRAVEL_SURFACE)));

		BiomeFeatureInstaller.installFeatures(biome, true, true, true, true, true, true);
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(StoneReplacer.configuredFeature()));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(OreSpawnOreGeneration.configuredFeature()));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(FluidDepositFeature.configuredFeature()));
		CompositeFeature<?, ?> surfaces = BiomeSurfaceFeature.configuredFeature();
		CompositeFeature<?, ?> bedrock = FlatBedrockFeature.configuredFeature();
		assertTrue(biome.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(surfaces));
		assertFalse(biome.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(surfaces));
		assertTrue(biome.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(bedrock));
		assertFalse(biome.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(bedrock));

		Biome inactive = new TestBiome(new Biome.BiomeBuilder()
				.precipitation(Biome.RainType.NONE).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(0.7F).downfall(0.8F)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(new CompositeSurfaceBuilder<>(Biome.DEFAULT_SURFACE_BUILDER,
						Biome.GRASS_DIRT_GRAVEL_SURFACE)));
		BiomeFeatureInstaller.installFeatures(inactive, false, false, false, false, false, false);
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(StoneReplacer.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(OreSpawnOreGeneration.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES)
				.contains(FluidDepositFeature.configuredFeature()));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS).contains(surfaces));
		assertFalse(inactive.getFeatures(GenerationStage.Decoration.TOP_LAYER_MODIFICATION).contains(bedrock));

		String context = source("FeaturePlaceContext.java");
		assertTrue(context.contains("(origin.getX() >> 4) + 1")
				&& context.contains("(origin.getZ() >> 4) + 1"),
				"Forge 25 whole-chunk features must advance from the northwest decoration origin");
		for (String feature : new String[] { "StoneReplacer.java", "OreSpawnOreGeneration.java",
				"FluidDepositFeature.java", "BiomeSurfaceFeature.java", "FlatBedrockFeature.java" }) {
			assertTrue(source(feature).contains("context.decorationChunk()"),
					feature + " must target Forge 25's center decoration chunk");
		}
		assertFalse(source("VanillaSpringCompatibility.java").contains("context.decorationChunk()"),
				"positioned spring features must retain the exact decoration origin");
		String fluidSource = source("FluidDepositFeature.java");
		assertTrue(fluidSource.contains("instanceof WorldGenRegion"));
		assertTrue(fluidSource.contains("isChunkInBounds(x >> 4, z >> 4)"),
				"fluid envelope reads must reject positions outside Forge 25's decoration region");
	}

	private static String source(String name) throws java.io.IOException {
		return new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "worldgen", name)), StandardCharsets.UTF_8);
	}

	private static void setConfiguredFeature(Class<?> owner, Feature<NoFeatureConfig> feature)
			throws ReflectiveOperationException {
		java.lang.reflect.Field field = owner.getDeclaredField("configuredFeature");
		field.setAccessible(true);
		field.set(null, Biome.createCompositeFeature(feature, new NoFeatureConfig(),
				Biome.PASSTHROUGH, IPlacementConfig.NO_PLACEMENT_CONFIG));
	}

	private static final class TestBiome extends Biome {
		TestBiome(Biome.BiomeBuilder builder) { super(builder); }
	}
}
