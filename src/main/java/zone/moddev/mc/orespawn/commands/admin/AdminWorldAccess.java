package zone.moddev.mc.orespawn.commands.admin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Heightmap;

/** Loaded-only access for jobs; none of these lookups may request new terrain. */
final class AdminWorldAccess {
    private AdminWorldAccess() { }
    static Chunk loaded(WorldServer world, int x, int z) {
        IChunk chunk = world.getChunkProvider().chunkExists(x, z)
                ? world.getChunkProvider().provideChunk(x, z, false, false) : null;
        return chunk instanceof Chunk ? (Chunk) chunk : null;
    }
    static WorldServer overworld(MinecraftServer server) {
        return server.forgeGetWorldMap().get(DimensionType.OVERWORLD);
    }
    static int height(Chunk chunk, int x, int z) {
        return chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
    }
    static void repairHeightmaps(Chunk chunk) {
        for (Heightmap.Type type : chunk.getHeightmaps()) chunk.getHeightmap(type).generate();
        chunk.generateSkylightMap();
        chunk.markDirty();
    }
}
