package zone.moddev.mc.orespawn.commands.admin;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

/** Retains unfinished height/light work across unloads without resuming block edits. */
public final class AdminLighting {
    private static final String TAG = "OreSpawnAdminLighting";
    private static final Map<Key, Pending> PENDING = new ConcurrentHashMap<>();
    private AdminLighting() { }

    static void changed(ServerWorld world, Chunk chunk, BlockPos position) {
        Pending pending = PENDING.computeIfAbsent(new Key(world, chunk.getPos().x, chunk.getPos().z), ignored -> new Pending(chunk));
        synchronized (pending) {
            pending.cells.set((position.getY() << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15));
            pending.heightsReady = false;
            pending.light = null;
        }
    }
    static boolean complete(ServerWorld world, Chunk chunk) {
        Key key = new Key(world, chunk.getPos().x, chunk.getPos().z);
        Pending pending = PENDING.get(key);
        if (pending == null) return true;
        synchronized (pending) {
            if (pending.light == null) pending.light = world.getChunkProvider().getLightManager().lightChunk(chunk, false);
            if (!pending.light.isDone()) return false;
            pending.light.join();
            PENDING.remove(key, pending);
            return true;
        }
    }
    public static boolean isPending(ServerWorld world, Chunk chunk) {
        return PENDING.containsKey(new Key(world, chunk.getPos().x, chunk.getPos().z));
    }
    static void recover(ServerWorld world, Chunk chunk) {
        Pending pending = PENDING.get(new Key(world, chunk.getPos().x, chunk.getPos().z));
        if (pending != null) synchronized (pending) { pending.recovery = true; }
    }
    public static void onChunkAvailable(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (!(event.getWorld() instanceof ServerWorld) || !(event.getChunk() instanceof Chunk)) return;
        Chunk chunk = (Chunk) event.getChunk();
        PENDING.computeIfPresent(new Key((ServerWorld) event.getWorld(), chunk.getPos().x, chunk.getPos().z), (key, previous) -> {
            synchronized (previous) {
                if (previous.chunk == chunk) { previous.recovery = true; return previous; }
                Pending next = new Pending(chunk);
                next.cells.or(previous.cells); next.recovery = true;
                return next;
            }
        });
    }
    public static void onSave(ChunkDataEvent.Save event) {
        if (!(event.getWorld() instanceof ServerWorld) || !(event.getChunk() instanceof Chunk)) return;
        Chunk chunk = (Chunk) event.getChunk();
        Pending pending = PENDING.get(new Key((ServerWorld) event.getWorld(), chunk.getPos().x, chunk.getPos().z));
        if (pending == null) { event.getData().remove(TAG); event.getData().getCompound("Level").remove(TAG); return; }
        CompoundNBT marker = new CompoundNBT();
        synchronized (pending) { marker.putIntArray("cells", pending.cells.stream().toArray()); }
        marker.putInt("format", 1);
        marker.putBoolean("relight_required", true);
        event.getData().put(TAG, marker);
        CompoundNBT levelData = event.getData().getCompound("Level");
        levelData.put(TAG, marker.copy()); event.getData().put("Level", levelData);
    }
    public static void onLoad(ChunkDataEvent.Load event) {
        if (!(event.getWorld() instanceof ServerWorld) || !(event.getChunk() instanceof Chunk)) return;
        Chunk chunk = (Chunk) event.getChunk();
        Key key = new Key((ServerWorld) event.getWorld(), chunk.getPos().x, chunk.getPos().z);
        PENDING.remove(key);
        CompoundNBT marker = event.getData().getCompound(TAG);
        if (marker.getInt("format") != 1 || !marker.contains("cells", 11)) return;
        Pending pending = new Pending(chunk);
        for (int cell : marker.getIntArray("cells")) {
            if (cell < 0 || cell >= 65536) {
                org.apache.logging.log4j.LogManager.getLogger().warn(
                        "Invalid interrupted admin-lighting marker at {}; use its edit journal for checked recovery", chunk.getPos());
                return;
            }
            pending.cells.set(cell);
        }
        if (!pending.cells.isEmpty() || marker.getBoolean("relight_required")) { pending.recovery = true; PENDING.put(key, pending); }
    }

    /** Uses the caller's shared tick budget and only the chunks already available. */
    static int tick(long deadline, int maximumVisits) {
        int visits = 0;
        for (Map.Entry<Key, Pending> entry : PENDING.entrySet()) {
            if (System.nanoTime() >= deadline || visits >= maximumVisits) break;
            Key key = entry.getKey(); Pending pending = entry.getValue();
            if (!pending.recovery || AdminWorldAccess.loaded(key.world, key.x, key.z) != pending.chunk) continue;
            if (!key.world.isAreaLoaded(new BlockPos((key.x << 4) - 17, 0, (key.z << 4) - 17),
                    new BlockPos((key.x << 4) + 32, 255, (key.z << 4) + 32))) continue;
            synchronized (pending) {
                if (!pending.heightsReady) {
                    AdminWorldAccess.repairHeightmaps(pending.chunk);
                    pending.heightsReady = true;
                }
                for (int cell = pending.cells.nextSetBit(0); cell >= 0 && System.nanoTime() < deadline
                        && visits < maximumVisits; cell = pending.cells.nextSetBit(cell + 1)) {
                    BlockPos at = new BlockPos((key.x << 4) + (cell & 15), cell >>> 8,
                            (key.z << 4) + ((cell >>> 4) & 15));
                    if (!AdminWorldAccess.checkLight(key.world, at)) break;
                    pending.cells.clear(cell); visits++;
                }
                pending.chunk.markDirty();
                if (pending.cells.isEmpty()) {
                    try { complete(key.world, pending.chunk); }
                    catch (java.util.concurrent.CompletionException | java.util.concurrent.CancellationException failure) {
                        // Keep the saved marker and journal; a failed lighting worker must not crash the server tick.
                        pending.recovery = false;
                        org.apache.logging.log4j.LogManager.getLogger().warn(
                                "Could not finish interrupted OreSpawn lighting at {}; its journal remains available", pending.chunk.getPos(), failure);
                    }
                }
            }
        }
        return visits;
    }
    public static void clear() { PENDING.clear(); }

    private static final class Pending {
        final Chunk chunk; final BitSet cells = new BitSet(65536);
        boolean recovery, heightsReady;
        java.util.concurrent.CompletableFuture<?> light;
        Pending(Chunk chunk) { this.chunk = chunk; }
    }
    private static final class Key {
        final ServerWorld world; final int x, z;
        Key(ServerWorld world, int x, int z) { this.world = world; this.x = x; this.z = z; }
        @Override public int hashCode() { return (System.identityHashCode(world) * 31 + x) * 31 + z; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return world == other.world && x == other.x && z == other.z;
        }
    }
}
