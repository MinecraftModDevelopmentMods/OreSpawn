package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;

class BiomeOverlaySourceTest {
	private static Biome source;
	private static Biome output;

	@BeforeAll
	static void bootstrapMinecraft() {
		Bootstrap.register();
		source = biome(0.7F, 0.8F, SurfaceBuilder.GRASS_DIRT_GRAVEL_CONFIG);
		output = biome(1.35F, 0.15F, SurfaceBuilder.STONE_STONE_GRAVEL_CONFIG);
	}

	@Test
	void blockCoordinatesMapToFloorDividedQuartCoordinatesAcrossNegativeEdges() {
		BiomeOverlaySource overlay = overlay(0.5D);
		boolean observedReplacement = false;
		for (int blockX = -65; blockX <= 65; blockX++) {
			for (int blockZ = -9; blockZ <= 9; blockZ++) {
				Biome expected = overlay.func_222366_b(
						Math.floorDiv(blockX, 4), Math.floorDiv(blockZ, 4));
				Biome actual = overlay.getBiome(blockX, blockZ);
				assertEquals(expected, actual, "block-to-quart conversion at " + blockX + "," + blockZ);
				observedReplacement |= actual == output;
			}
		}
		assertTrue(observedReplacement, "deterministic palette must exercise provider output");
	}

	@Test
	void bulkBlockQueryUsesTheSameCoordinateContract() {
		BiomeOverlaySource overlay = overlay(0.5D);
		Biome[] values = overlay.getBiomes(-9, -7, 11, 9, false);
		for (int z = 0; z < 9; z++) {
			for (int x = 0; x < 11; x++) {
				assertEquals(overlay.func_222366_b(Math.floorDiv(-9 + x, 4),
						Math.floorDiv(-7 + z, 4)), values[x + z * 11]);
			}
		}
	}

	@Test
	void squareSearchAndSurfaceQueriesIncludeProviderBiomes() {
		BiomeOverlaySource overlay = overlay(1.0D);
		assertTrue(overlay.getBiomesInSquare(-9, -9, 8).contains(output));
		BlockPos found = overlay.findBiomePosition(-9, -9, 8,
				Collections.singletonList(output), new Random(0L));
		assertNotNull(found);
		assertEquals(0, found.getX() & 3);
		assertEquals(0, found.getZ() & 3);
		assertTrue(overlay.getSurfaceBlocks().contains(
				output.getSurfaceBuilderConfig().getTop()));
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

	private static Biome biome(float temperature, float downfall,
			net.minecraft.world.gen.surfacebuilders.SurfaceBuilderConfig surface) {
		return new TestBiome(new Biome.Builder()
				.precipitation(Biome.RainType.RAIN).category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(temperature).downfall(downfall)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(SurfaceBuilder.DEFAULT, surface));
	}

	private static final class TestBiome extends Biome {
		TestBiome(Biome.Builder builder) { super(builder); }
	}

	private static final class ConstantBiomeProvider extends BiomeProvider {
		private final Biome biome;

		ConstantBiomeProvider(Biome biome) { this.biome = biome; }

		@Override public Biome getBiome(int x, int z) { return biome; }

		@Override
		public Biome[] getBiomes(int x, int z, int width, int length, boolean cacheFlag) {
			Biome[] result = new Biome[width * length];
			java.util.Arrays.fill(result, biome);
			return result;
		}

		@Override public Set<Biome> getBiomesInSquare(int x, int z, int radius) {
			return Collections.singleton(biome);
		}

		@Override public BlockPos findBiomePosition(int x, int z, int range,
				List<Biome> biomes, Random random) { return null; }
		@Override public boolean hasStructure(Structure<?> structure) { return false; }
		@Override public Set<BlockState> getSurfaceBlocks() {
			return Collections.singleton(biome.getSurfaceBuilderConfig().getTop());
		}
	}
}
