package zone.moddev.mc.orespawn.worldgen;

import java.lang.ref.WeakReference;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.OverworldChunkGenerator;

/** Native density heights, cached without requesting or constructing world chunks. */
final class NaturalTerrainSurface {
    private final WeakReference<ServerWorld> world;
    NaturalTerrainSurface(ServerWorld world) { this.world = new WeakReference<>(world); }

    static OverworldChunkGenerator nativeGenerator(ServerWorld world) {
        ChunkGenerator<?> generator = world.getChunkProvider().getChunkGenerator();
        // Custom subclasses can change terrain or population order independently.
        return generator != null && generator.getClass() == OverworldChunkGenerator.class
                ? (OverworldChunkGenerator) generator : null;
    }

    int at(int x, int z) {
        ServerWorld world = this.world.get();
        if (world == null) return -1;
        OverworldChunkGenerator generator = nativeGenerator(world);
        if (generator == null) return -1;
        return NativeDensityTerrain.chunk(generator, x >> 4, z >> 4).terrainTop(x & 15, z & 15);
    }
}
