package zone.moddev.mc.orespawn.worldgen;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.NoiseChunkGenerator;
import net.minecraft.world.gen.OverworldChunkGenerator;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/** Shares vanilla density columns between formation heights and background-fluid masks. */
final class NativeDensityTerrain {
    private static final Map<OverworldChunkGenerator, Cache> CACHES = new WeakHashMap<>();
    private NativeDensityTerrain() { }

    static synchronized Data chunk(OverworldChunkGenerator generator, int x, int z) {
        Cache cache = CACHES.get(generator);
        if (cache == null || cache.provider.get() != generator.biomeProvider) {
            cache = new Cache(generator.biomeProvider);
            CACHES.put(generator, cache);
        }
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Data data = cache.chunks.get(key);
        if (data == null) {
            data = new Data(generator, x, z);
            cache.chunks.put(key, data);
        }
        return data;
    }

    static synchronized void clear() { CACHES.clear(); }

    private static final class Cache {
        final WeakReference<BiomeProvider> provider;
        final Map<Long, Data> chunks = new LinkedHashMap<Long, Data>(128, .75F, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, Data> entry) { return size() > 512; }
        };
        Cache(BiomeProvider provider) { this.provider = new WeakReference<>(provider); }
    }

    private static final class NativeColumn {
        static final Method FILL = find();

        private static Method find() {
            // Use the mapped workspace name, with Forge remapping for the packaged runtime.
            try {
                Method method = NoiseChunkGenerator.class.getDeclaredMethod("fillNoiseColumn",
                        double[].class, int.class, int.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException mappedWorkspace) {
                return ObfuscationReflectionHelper.findMethod(NoiseChunkGenerator.class,
                        "func_222548_a", double[].class, int.class, int.class);
            }
        }
    }

    static final class Data {
        // Vanilla 1.15 Overworld terrain interpolates 4-by-8 density cells.
        private final double[][][] noise = new double[5][5][33];
        private final short[] heights = new short[256];
        private final int seaLevel;

        Data(OverworldChunkGenerator generator, int cx, int cz) {
            seaLevel = generator.getSeaLevel();
            Arrays.fill(heights, (short) -2);
            try {
                for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++)
                    NativeColumn.FILL.invoke(generator, noise[x][z], cx * 4 + x, cz * 4 + z);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not sample native Overworld density", failure);
            }
        }

        double density(int x, int y, int z) {
            int nx = x >> 2, nz = z >> 2, ny = y >> 3;
            return MathHelper.lerp3((y & 7) / 8.0, (x & 3) / 4.0, (z & 3) / 4.0,
                    noise[nx][nz][ny], noise[nx][nz][ny + 1],
                    noise[nx + 1][nz][ny], noise[nx + 1][nz][ny + 1],
                    noise[nx][nz + 1][ny], noise[nx][nz + 1][ny + 1],
                    noise[nx + 1][nz + 1][ny], noise[nx + 1][nz + 1][ny + 1]);
        }

        boolean nativeFluid(int x, int y, int z) { return y >= 0 && y < seaLevel && !(density(x, y, z) > 0.0); }

        synchronized int terrainTop(int x, int z) {
            int index = z * 16 + x;
            if (heights[index] != -2) return heights[index];
            for (int y = 255; y >= 0; y--) {
                if (density(x, y, z) > 0.0) return heights[index] = (short) y;
                if (y < seaLevel) return heights[index] = -1;
            }
            return heights[index] = -1;
        }
    }
}
