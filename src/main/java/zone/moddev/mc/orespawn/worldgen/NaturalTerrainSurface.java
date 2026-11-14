package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.ref.WeakReference;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorOverworld;

/** Bounded height cache; never calls provideChunk or generates a neighbouring world chunk. */
final class NaturalTerrainSurface {
    private final WeakReference<WorldServer> world;
    private final Map<Long, short[]> cache = new LinkedHashMap<Long, short[]>(128, .75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, short[]> e) { return size() > 512; }
    };
    NaturalTerrainSurface(WorldServer world) { this.world = new WeakReference<>(world); }

    static ChunkGeneratorOverworld nativeGenerator(WorldServer world) {
        net.minecraft.world.gen.IChunkGenerator<?> generator = world.getChunkProvider().getChunkGenerator();
        // Amplified and Large Biomes use this same implementation.
        // Subclasses may change population order or lake geometry, so exclude them.
        return generator != null && generator.getClass() == ChunkGeneratorOverworld.class
                ? (ChunkGeneratorOverworld) generator : null;
    }

    synchronized int at(int x, int z) {
        WorldServer world = this.world.get();
        if (world == null) return -1;
        ChunkGeneratorOverworld generator = nativeGenerator(world);
        if (generator == null) return -1;
        int cx = x >> 4, cz = z >> 4;
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        short[] heights = cache.get(key);
        if (heights == null) {
            heights = new short[256];
            ChunkPrimer primer = new ChunkPrimer(cx, cz, null);
            generator.setBlocksInChunk(cx, cz, primer);
            net.minecraft.util.math.BlockPos.MutableBlockPos cursor = new net.minecraft.util.math.BlockPos.MutableBlockPos();
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int top = -1;
                for (int y = 255; y >= 0; y--) {
                    IBlockState state = primer.getBlockState(cursor.setPos(dx, y, dz));
                    if (state.getMaterial() == Material.AIR) continue;
                    top = state.getMaterial().isLiquid() ? -1 : y;
                    break;
                }
                heights[dz * 16 + dx] = (short) top;
            }
            cache.put(key, heights);
        }
        return heights[(z & 15) * 16 + (x & 15)];
    }
}
