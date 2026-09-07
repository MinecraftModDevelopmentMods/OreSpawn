package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeContainer;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;

class TerrainBiomeLookupTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.register();
	}

	@Test
	void geologyAndSamplerHeightsResolveThroughTheSameQuartBiome() {
		Biome lower = biome(0.4F);
		Biome upper = biome(0.8F);
		Biome[] cells = new Biome[BiomeContainer.BIOMES_SIZE];
		Arrays.fill(cells, lower);
		for (int quartY = 16; quartY <= BiomeContainer.VERTICAL_MASK; quartY++) {
			for (int quartZ = 0; quartZ <= BiomeContainer.HORIZONTAL_MASK; quartZ++) {
				for (int quartX = 0; quartX <= BiomeContainer.HORIZONTAL_MASK; quartX++) {
					cells[(quartY << 4) | (quartZ << 2) | quartX] = upper;
				}
			}
		}
		BiomeContainer container = new BiomeContainer(cells);

		assertEquals(3, TerrainBiomeLookup.quart(13));
		assertEquals(15, TerrainBiomeLookup.quart(62));
		assertEquals(15, TerrainBiomeLookup.quart(63),
				"later surface work must not move an adjacent height into a fuzzy biome cell");
		assertEquals(lower, TerrainBiomeLookup.atBlock(container, 13, 63, -32));
		assertEquals(upper, TerrainBiomeLookup.atBlock(container, 13, 64, -32),
				"the direct lookup must retain Forge 1.15's vertical biome boundary");
		assertEquals(-8, TerrainBiomeLookup.quart(-32));
		assertEquals(-1, TerrainBiomeLookup.quart(-1),
				"negative block coordinates must use floor-by-four quart coordinates");
	}

	private static Biome biome(float temperature) {
		return new TestBiome(new Biome.Builder()
				.precipitation(Biome.RainType.NONE)
				.category(Biome.Category.NONE)
				.depth(0.0F).scale(0.0F).temperature(temperature).downfall(0.5F)
				.waterColor(0x3F76E4).waterFogColor(0x050533)
				.surfaceBuilder(SurfaceBuilder.DEFAULT,
						SurfaceBuilder.GRASS_DIRT_GRAVEL_CONFIG));
	}

	private static final class TestBiome extends Biome {
		TestBiome(Biome.Builder builder) { super(builder); }
	}
}
