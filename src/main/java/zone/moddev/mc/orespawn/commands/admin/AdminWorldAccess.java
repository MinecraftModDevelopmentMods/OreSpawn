package zone.moddev.mc.orespawn.commands.admin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Heightmap;

/** Loaded-only access for jobs; none of these lookups may request new terrain. */
final class AdminWorldAccess {
    private AdminWorldAccess() { }
    static Chunk loaded(ServerWorld world, int x, int z) {
        return world.getChunkProvider().func_225313_a(x, z);
    }
    static ServerWorld overworld(MinecraftServer server) {
        return server.forgeGetWorldMap().get(DimensionType.OVERWORLD);
    }
    static int height(Chunk chunk, int x, int z) {
        return chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
    }
    static void repairHeightmaps(Chunk chunk) {
        Heightmap.func_222690_a(chunk, java.util.EnumSet.allOf(Heightmap.Type.class));
        net.minecraft.world.lighting.WorldLightManager light = chunk.getWorld().getChunkProvider().getLightManager();
        net.minecraft.world.chunk.ChunkSection[] sections = chunk.getSections();
        for (int y = 0; y < sections.length; y++)
            light.updateSectionStatus(net.minecraft.util.math.SectionPos.from(chunk.getPos(), y),
                    net.minecraft.world.chunk.ChunkSection.isEmpty(sections[y]));
        chunk.markDirty();
    }

    static boolean checkLight(ServerWorld world, net.minecraft.util.math.BlockPos pos) {
        if (loaded(world, pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
        world.getChunkProvider().getLightManager().checkBlock(pos);
        return true;
    }
}
