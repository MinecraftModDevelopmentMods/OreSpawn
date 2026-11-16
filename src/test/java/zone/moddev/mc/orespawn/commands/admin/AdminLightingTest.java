package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.orespawn.test.TestObjects.allocate;
import java.lang.reflect.Field;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminLightingTest {
    @AfterEach void clear() { AdminLighting.clear(); }

    @Test void exactChangedCellsSurviveRepeatedSaveAndReload() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, -20, 21);
        AdminLighting.changed(world, chunk, new BlockPos(-320, 0, 336));
        AdminLighting.changed(world, chunk, new BlockPos(-305, 255, 351));
        CompoundNBT data = save(chunk);
        for (int restart = 0; restart < 3; restart++) {
            AdminLighting.clear();
            AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, data.contains("Level", 10) ? data.getCompound("Level") : data, net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
            assertTrue(AdminLighting.isPending(world, chunk));
            data = save(chunk);
            assertArrayEquals(new int[]{0, 65535}, data.getCompound("OreSpawnAdminLighting").getIntArray("cells"));
        }
        pendingFuture(java.util.concurrent.CompletableFuture.completedFuture(null));
        assertTrue(AdminLighting.complete(world, chunk));
        assertFalse(save(chunk).contains("OreSpawnAdminLighting"));
    }

    @Test void worldsAndChunksRemainIndependent() throws Exception {
        ServerWorld a = allocate(ServerWorld.class), b = allocate(ServerWorld.class);
        Chunk first = chunk(a, -1, -1), second = chunk(b, -1, -1), neighbour = chunk(a, -2, -1);
        AdminLighting.changed(a, first, new BlockPos(-1, 12, -1));
        assertTrue(AdminLighting.isPending(a, first));
        assertFalse(AdminLighting.isPending(b, second));
        assertFalse(AdminLighting.isPending(a, neighbour));
        assertFalse(save(second).contains("OreSpawnAdminLighting"));
        assertFalse(save(neighbour).contains("OreSpawnAdminLighting"));
    }

    @Test void absentMalformedAndOutOfRangeMarkersNeverResumeEdits() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, 0, 0);
        for (int[] cells : new int[][]{new int[0], {-1}, {65536}, {0, 65536}}) {
            AdminLighting.changed(world, chunk, new BlockPos(1, 1, 1));
            CompoundNBT data = new CompoundNBT(), marker = new CompoundNBT();
            marker.putInt("format", 1); marker.putIntArray("cells", cells);
            data.put("OreSpawnAdminLighting", marker);
            AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, data, net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
            assertFalse(AdminLighting.isPending(world, chunk));
        }
        AdminLighting.changed(world, chunk, new BlockPos(1, 1, 1));
        AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, new CompoundNBT(), net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
        assertFalse(AdminLighting.isPending(world, chunk));
        assertEquals(0, AdminLighting.tick(System.nanoTime() + 1000000, 16384));
    }

    @Test void asyncBarrierRetainsItsMarkerUntilTheNativeFutureCompletes() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, 0, 0);
        AdminLighting.changed(world, chunk, new BlockPos(0, 15, 0));
        java.util.concurrent.CompletableFuture<Void> nativeWork = new java.util.concurrent.CompletableFuture<>();
        pendingFuture(nativeWork);
        assertFalse(AdminLighting.complete(world, chunk));
        assertTrue(save(chunk).getCompound("OreSpawnAdminLighting").getBoolean("relight_required"));
        nativeWork.complete(null);
        assertTrue(AdminLighting.complete(world, chunk));
        assertFalse(save(chunk).contains("OreSpawnAdminLighting"));
    }

    @Test void anEmptyCellListStillRestoresAnUnfinishedNativeLightBarrier() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, 0, 0);
        CompoundNBT data = new CompoundNBT(), marker = new CompoundNBT();
        marker.putInt("format", 1); marker.putIntArray("cells", new int[0]);
        marker.putBoolean("relight_required", true); data.put("OreSpawnAdminLighting", marker);
        AdminLighting.onLoad(new ChunkDataEvent.Load(chunk, data, net.minecraft.world.chunk.ChunkStatus.Type.LEVELCHUNK));
        assertTrue(AdminLighting.isPending(world, chunk));
        pendingFuture(java.util.concurrent.CompletableFuture.completedFuture(null));
        assertTrue(AdminLighting.complete(world, chunk));
    }

    private static void pendingFuture(java.util.concurrent.CompletableFuture<?> future) throws Exception {
        Field field = AdminLighting.class.getDeclaredField("PENDING"); field.setAccessible(true);
        java.util.Map<?, ?> pending = (java.util.Map<?, ?>) field.get(null);
        assertEquals(1, pending.size());
        Object entry = pending.values().iterator().next();
        set(entry.getClass(), entry, "light", future);
    }

    @Test void failedNativeLightingKeepsTheRecoveryMarker() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, 0, 0);
        AdminLighting.changed(world, chunk, new BlockPos(0, 15, 0));
        java.util.concurrent.CompletableFuture<Void> nativeWork = new java.util.concurrent.CompletableFuture<>();
        nativeWork.completeExceptionally(new IllegalStateException("worker failed"));
        pendingFuture(nativeWork);
        assertThrows(java.util.concurrent.CompletionException.class, () -> AdminLighting.complete(world, chunk));
        assertTrue(save(chunk).getCompound("OreSpawnAdminLighting").getBoolean("relight_required"));
    }

    @Test void anotherEditCannotReuseAnOlderLightBarrier() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk chunk = chunk(world, 0, 0);
        AdminLighting.changed(world, chunk, new BlockPos(0, 15, 0));
        pendingFuture(java.util.concurrent.CompletableFuture.completedFuture(null));
        AdminLighting.changed(world, chunk, new BlockPos(1, 15, 0));
        Field field = AdminLighting.class.getDeclaredField("PENDING"); field.setAccessible(true);
        Object pending = ((java.util.Map<?, ?>) field.get(null)).values().iterator().next();
        Field light = pending.getClass().getDeclaredField("light"); light.setAccessible(true);
        assertNull(light.get(pending));
        assertArrayEquals(new int[]{3840, 3841}, save(chunk).getCompound("OreSpawnAdminLighting").getIntArray("cells"));
    }

    @Test void aHotReloadRetainsCleanupWithoutResumingTheInterruptedEdit() throws Exception {
        ServerWorld world = allocate(ServerWorld.class);
        Chunk first = chunk(world, -1, 2), reloaded = chunk(world, -1, 2);
        AdminLighting.changed(world, first, new BlockPos(-16, 15, 32));
        AdminLighting.recover(world, first);
        AdminLighting.onChunkAvailable(new net.minecraftforge.event.world.ChunkEvent.Load(reloaded));
        assertArrayEquals(new int[]{3840}, save(reloaded).getCompound("OreSpawnAdminLighting").getIntArray("cells"));
        Field field = AdminLighting.class.getDeclaredField("PENDING"); field.setAccessible(true);
        Object pending = ((java.util.Map<?, ?>) field.get(null)).values().iterator().next();
        Field retained = pending.getClass().getDeclaredField("chunk"); retained.setAccessible(true);
        assertSame(reloaded, retained.get(pending));
    }

    private static CompoundNBT save(Chunk chunk) {
        CompoundNBT data = new CompoundNBT();
        AdminLighting.onSave(new ChunkDataEvent.Save(chunk, data));
        return data;
    }
    private static Chunk chunk(ServerWorld world, int x, int z) throws Exception {
        Chunk chunk = allocate(Chunk.class);
        set(Chunk.class, chunk, "world", world);
        set(Chunk.class, chunk, "pos", new net.minecraft.util.math.ChunkPos(x, z));
        return chunk;
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
