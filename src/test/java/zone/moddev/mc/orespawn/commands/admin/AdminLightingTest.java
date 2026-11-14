package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.orespawn.test.TestObjects.allocate;
import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminLightingTest {
    @AfterEach void clear() { AdminLighting.clear(); }

    @Test void exactChangedCellsSurviveRepeatedSaveAndReload() throws Exception {
        WorldServer world = allocate(WorldServer.class);
        Chunk chunk = chunk(world, -20, 21);
        AdminLighting.changed(world, chunk, new BlockPos(-320, 0, 336));
        AdminLighting.changed(world, chunk, new BlockPos(-305, 255, 351));
        NBTTagCompound data = save(chunk);
        for (int restart = 0; restart < 3; restart++) {
            AdminLighting.clear();
            AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, data));
            assertTrue(AdminLighting.isPending(world, chunk));
            data = save(chunk);
            assertArrayEquals(new int[]{0, 65535}, data.getCompound("OreSpawnAdminLighting").getIntArray("cells"));
        }
        AdminLighting.complete(world, chunk);
        assertFalse(save(chunk).hasKey("OreSpawnAdminLighting"));
    }

    @Test void worldsAndChunksRemainIndependent() throws Exception {
        WorldServer a = allocate(WorldServer.class), b = allocate(WorldServer.class);
        Chunk first = chunk(a, -1, -1), second = chunk(b, -1, -1), neighbour = chunk(a, -2, -1);
        AdminLighting.changed(a, first, new BlockPos(-1, 12, -1));
        assertTrue(AdminLighting.isPending(a, first));
        assertFalse(AdminLighting.isPending(b, second));
        assertFalse(AdminLighting.isPending(a, neighbour));
        assertFalse(save(second).hasKey("OreSpawnAdminLighting"));
        assertFalse(save(neighbour).hasKey("OreSpawnAdminLighting"));
    }

    @Test void absentMalformedAndOutOfRangeMarkersNeverResumeEdits() throws Exception {
        WorldServer world = allocate(WorldServer.class);
        Chunk chunk = chunk(world, 0, 0);
        for (int[] cells : new int[][]{new int[0], {-1}, {65536}, {0, 65536}}) {
            AdminLighting.changed(world, chunk, new BlockPos(1, 1, 1));
            NBTTagCompound data = new NBTTagCompound(), marker = new NBTTagCompound();
            marker.setInt("format", 1); marker.setIntArray("cells", cells);
            data.setTag("OreSpawnAdminLighting", marker);
            AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, data));
            assertFalse(AdminLighting.isPending(world, chunk));
        }
        AdminLighting.changed(world, chunk, new BlockPos(1, 1, 1));
        AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, new NBTTagCompound()));
        assertFalse(AdminLighting.isPending(world, chunk));
        assertEquals(0, AdminLighting.tick(System.nanoTime() + 1000000, 16384));
    }

    private static NBTTagCompound save(Chunk chunk) {
        NBTTagCompound data = new NBTTagCompound();
        AdminLighting.onSave(new ChunkDataEvent.Save(chunk, data));
        return data;
    }
    private static Chunk chunk(WorldServer world, int x, int z) throws Exception {
        Chunk chunk = allocate(Chunk.class);
        set(Chunk.class, chunk, "world", world);
        set(Chunk.class, chunk, "x", x); set(Chunk.class, chunk, "z", z);
        return chunk;
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
