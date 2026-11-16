package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.orespawn.test.TestObjects.allocate;
import java.lang.reflect.Field;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.OverworldChunkGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;

class NativeDensityTerrainTest {
    @BeforeAll static void bootstrap() { Forge31TestBootstrap.registerVanilla(); }
    @AfterEach void clear() { NativeDensityTerrain.clear(); }

    @Test void interpolatesTheNativeVerticalAndHorizontalAxes() throws Exception {
        NativeDensityTerrain.Data data = new NativeDensityTerrain.Data(generator(0), 0, 0);
        assertEquals(32.0, data.density(0, 0, 0));
        assertEquals(32 - 19 + .1 * 7 / 4 + .2 * 11 / 4, data.density(7, 19, 11), 1e-9);
        assertEquals(32 - 255 + .1 * 15 / 4 + .2 * 15 / 4, data.density(15, 255, 15), 1e-9);
    }

    @Test void negativeChunkCoordinatesKeepTheirNativeNoiseOrigin() throws Exception {
        NativeDensityTerrain.Data data = new NativeDensityTerrain.Data(generator(0), -3, -4);
        assertEquals(32 - .1 * 12 - .2 * 16, data.density(0, 0, 0), 1e-9);
        assertEquals(27, data.terrainTop(0, 0));
    }

    @Test void fluidMaskRejectsCountryRockAndStopsAtSeaLevel() throws Exception {
        NativeDensityTerrain.Data data = new NativeDensityTerrain.Data(generator(63), 0, 0);
        assertFalse(data.nativeFluid(0, 5, 0));
        assertTrue(data.nativeFluid(0, 32, 0));
        assertTrue(data.nativeFluid(0, 62, 0));
        assertFalse(data.nativeFluid(0, 63, 0));
        assertFalse(data.nativeFluid(0, -1, 0));
        assertFalse(data.nativeFluid(0, 256, 0));
    }

    @Test void waterCoveredColumnsDoNotBecomeDryFormationSurfaces() throws Exception {
        assertEquals(-1, new NativeDensityTerrain.Data(generator(63), 0, 0).terrainTop(0, 0));
        assertEquals(31, new NativeDensityTerrain.Data(generator(0), 0, 0).terrainTop(0, 0));
    }

    @Test void samplesEachDensityCornerOnceAndReusesTheChunk() throws Exception {
        Generator generator = generator(0);
        NativeDensityTerrain.Data data = NativeDensityTerrain.chunk(generator, -2, 3);
        assertEquals(25, generator.calls);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) data.terrainTop(x, z);
        assertSame(data, NativeDensityTerrain.chunk(generator, -2, 3));
        assertEquals(25, generator.calls);
    }

    @Test void differentGeneratorsAndProviderChangesCannotShareDensity() throws Exception {
        Generator generator = generator(0);
        NativeDensityTerrain.Data original = NativeDensityTerrain.chunk(generator, 0, 0);
        assertNotSame(original, NativeDensityTerrain.chunk(generator(0), 0, 0));
        Field provider = ChunkGenerator.class.getDeclaredField("biomeProvider"); provider.setAccessible(true);
        provider.set(generator, allocate(BiomeOverlaySource.class));
        assertNotSame(original, NativeDensityTerrain.chunk(generator, 0, 0));
        assertEquals(50, generator.calls);
    }

    @Test void cacheHasABoundedWorkingSetAndCanBeCleared() throws Exception {
        Generator generator = generator(0);
        NativeDensityTerrain.Data oldest = NativeDensityTerrain.chunk(generator, 0, 0);
        for (int x = 1; x <= 512; x++) NativeDensityTerrain.chunk(generator, x, 0);
        assertNotSame(oldest, NativeDensityTerrain.chunk(generator, 0, 0));
        NativeDensityTerrain.clear();
        assertEquals(514 * 25, generator.calls);
        NativeDensityTerrain.chunk(generator, 0, 0);
        assertEquals(515 * 25, generator.calls);
    }

    private static Generator generator(int seaLevel) throws Exception {
        Generator generator = allocate(Generator.class); generator.seaLevel = seaLevel; return generator;
    }

    private static final class Generator extends OverworldChunkGenerator {
        int seaLevel, calls;
        private Generator() { super(null, null, null); }
        @Override public int getSeaLevel() { return seaLevel; }
        @Override protected void fillNoiseColumn(double[] column, int x, int z) {
            calls++;
            for (int y = 0; y < column.length; y++) column[y] = 32 - y * 8 + x * .1 + z * .2;
        }
    }
}
