package zone.moddev.mc.orespawn.worldgen.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PerlinNoise2DTest {
    @Test
    void constructorKeepsFloatArithmeticExplicit() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/zone/moddev/mc/orespawn/worldgen/math/PerlinNoise2D.java"));
        assertFalse(source.contains("initialSize *= 0.5;"));
        assertFalse(source.contains("initialRange *= 0.5;"));
    }

    @Test
    void constructorMatchesTheOriginalDoubleLiteralCalculations() {
        long[] seeds = {0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, -4965128775892001975L};
        float[] ranges = {Float.MIN_VALUE, Float.MIN_NORMAL, 0.125F, 1F, 37.25F, 512F};
        float[] sizes = {0.125F, 1F, 31.75F, 256F, 4096F};
        double[] coordinates = {-1000000.25, -256.5, -0.125, 0, 0.5, 63.75, 1000000.25};
        for (long seed : seeds) {
            for (float range : ranges) {
                for (float size : sizes) {
                    for (int count : new int[] {0, 1, 2, 5, 12}) {
                        PerlinNoise2D current = new PerlinNoise2D(seed, range, size, count);
                        NoiseLayer2D[] original = originalLayers(seed, range, size, count);
                        for (double x : coordinates) {
                            for (double z : coordinates) {
                                float expected = 0F;
                                for (NoiseLayer2D layer : original) expected += layer.getValueAt(x, z);
                                assertEquals(Float.floatToIntBits(expected),
                                        Float.floatToIntBits(current.valueAt(x, z)),
                                        "Noise samples must retain their original float bits");
                            }
                        }
                    }
                }
            }
        }
    }

    private static NoiseLayer2D[] originalLayers(long seed, float range, float size, int count) {
        NoiseLayer2D[] layers = new NoiseLayer2D[count];
        for (int i = 0; i < count; i++) {
            seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            layers[i] = new NoiseLayer2D(seed, size, range);
            // Keep the old double calculation here so the comparison is independent of the fix.
            size = (float) (size * 0.5);
            range = (float) (range * 0.5);
        }
        return layers;
    }
}
