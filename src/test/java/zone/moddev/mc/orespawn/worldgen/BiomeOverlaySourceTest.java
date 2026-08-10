package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class BiomeOverlaySourceTest {
	private static Biome source;
	private static Biome output;

	@BeforeAll
	static void bootstrapMinecraft() {
		Forge14TestBootstrap.registerVanilla();
		source = biome("source", 0.7F, 0.8F, false);
		output = biome("output", 1.35F, 0.15F, true);
	}

	@Test
	void blockCoordinatesMapToFloorDividedGenerationCoordinatesAcrossNegativeEdges() {
		BiomeOverlaySource overlay = overlay(0.5D);
		boolean observedReplacement = false;
		for (int blockX = -65; blockX <= 65; blockX++) {
			for (int blockZ = -9; blockZ <= 9; blockZ++) {
				Biome expected = overlay.getBiomesForGeneration(null,
						Math.floorDiv(blockX, 4), Math.floorDiv(blockZ, 4), 1, 1)[0];
				Biome actual = overlay.getBiome(new BlockPos(blockX, 0, blockZ), source);
				assertEquals(expected, actual, "block-to-generation conversion at " + blockX + "," + blockZ);
				observedReplacement |= actual == output;
			}
		}
		assertTrue(observedReplacement, "deterministic palette must exercise provider output");
	}

	@Test
	void bulkBlockQueryUsesTheSameCoordinateContract() {
		BiomeOverlaySource overlay = overlay(0.5D);
		Biome[] values = overlay.getBiomes(null, -9, -7, 11, 9, false);
		for (int z = 0; z < 9; z++) {
			for (int x = 0; x < 11; x++) {
				assertEquals(overlay.getBiomesForGeneration(null, Math.floorDiv(-9 + x, 4),
						Math.floorDiv(-7 + z, 4), 1, 1)[0], values[x + z * 11]);
			}
		}
	}

	@Test
	void viabilitySearchAndSurfaceMaterialIncludeProviderBiomes() {
		BiomeOverlaySource overlay = overlay(1.0D);
		assertTrue(overlay.areBiomesViable(-9, -9, 8, Collections.singletonList(output)));
		BlockPos found = overlay.findBiomePosition(-9, -9, 8,
				Collections.singletonList(output), new Random(0L));
		assertNotNull(found);
		assertEquals(0, found.getX() & 3);
		assertEquals(0, found.getZ() & 3);
		assertEquals(Blocks.STONE.getDefaultState(), output.topBlock);
	}

	private static BiomeOverlaySource overlay(double coverage) {
		BakedBiomeWorldgen.Entry entry = new BakedBiomeWorldgen.Entry(output, 1.0D,
				Collections.emptySet(), -Float.MAX_VALUE, Float.MAX_VALUE,
				-Float.MAX_VALUE, Float.MAX_VALUE);
		BakedBiomeWorldgen.Choice choice = new BakedBiomeWorldgen.Choice(
				new Biome[] { output }, new double[] { 1.0D }, 0.0D, 1.0D, 73);
		Map<Biome, BakedBiomeWorldgen.Choice> choices = new IdentityHashMap<>();
		choices.put(source, choice);
		BakedBiomeWorldgen.Palette palette = new BakedBiomeWorldgen.Palette(
				17L, true, 0, 1, coverage, 0.0D,
				Collections.emptySet(), Collections.emptySet(),
				new BakedBiomeWorldgen.Entry[] { entry }, choices);
		return new BiomeOverlaySource(new ConstantBiomeProvider(source),
				Collections.singletonList(palette), 0L);
	}

	private static Biome biome(String name, float temperature, float rainfall, boolean stoneSurface) {
		TestBiome biome = new TestBiome(new Biome.BiomeProperties(name)
				.setBaseHeight(0.1F).setHeightVariation(0.2F)
				.setTemperature(temperature).setRainfall(rainfall).setWaterColor(0x3f76e4));
		if (stoneSurface) {
			biome.topBlock = Blocks.STONE.getDefaultState();
			biome.fillerBlock = Blocks.STONE.getDefaultState();
		}
		return biome;
	}

	private static final class TestBiome extends Biome {
		TestBiome(Biome.BiomeProperties properties) { super(properties); }
	}

	private static final class ConstantBiomeProvider extends BiomeProvider {
		private final Biome biome;

		ConstantBiomeProvider(Biome biome) { this.biome = biome; }

		@Override public Biome getBiome(BlockPos pos, Biome fallback) { return biome; }

		@Override
		public Biome[] getBiomesForGeneration(Biome[] reuse, int x, int z, int width, int length) {
			return filled(reuse, width, length);
		}

		@Override
		public Biome[] getBiomes(Biome[] reuse, int x, int z, int width, int length, boolean cacheFlag) {
			return filled(reuse, width, length);
		}

		private Biome[] filled(Biome[] reuse, int width, int length) {
			Biome[] result = reuse != null && reuse.length >= width * length
					? reuse : new Biome[width * length];
			Arrays.fill(result, 0, width * length, biome);
			return result;
		}

		@Override public List<Biome> getBiomesToSpawnIn() { return Collections.singletonList(biome); }
	}
}
