package zone.moddev.mc.orespawn.commands.admin;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

/** Retains unfinished height/light work across unloads without resuming block edits. */
public final class AdminLighting {
    private static final String TAG = "OreSpawnAdminLighting";
    private static final Map<Key, Pending> PENDING = new ConcurrentHashMap<>();
    private AdminLighting() { }

    static void changed(WorldServer world, Chunk chunk, BlockPos position) {
        Pending pending = PENDING.computeIfAbsent(new Key(world, chunk.x, chunk.z), ignored -> new Pending(chunk));
        synchronized (pending) {
            pending.cells.set((position.getY() << 8) | ((position.getZ() & 15) << 4) | (position.getX() & 15));
        }
    }
    static void complete(WorldServer world, Chunk chunk) { PENDING.remove(new Key(world, chunk.x, chunk.z)); }
    public static boolean isPending(WorldServer world, Chunk chunk) {
        return PENDING.containsKey(new Key(world, chunk.x, chunk.z));
    }
    public static void onSave(ChunkDataEvent.Save event) {
        if (!(event.getWorld() instanceof WorldServer) || !(event.getChunk() instanceof Chunk)) return;
        Chunk chunk = (Chunk) event.getChunk();
        Pending pending = PENDING.get(new Key((WorldServer) event.getWorld(), chunk.x, chunk.z));
        if (pending == null) { event.getData().removeTag(TAG); return; }
        NBTTagCompound marker = new NBTTagCompound();
        synchronized (pending) { marker.setIntArray("cells", pending.cells.stream().toArray()); }
        marker.setInt("format", 1);
        event.getData().setTag(TAG, marker);
    }
    public static void onLoad(ChunkDataEvent.Load event) {
        if (!(event.getWorld() instanceof WorldServer) || !(event.getChunk() instanceof Chunk)) return;
        Chunk chunk = (Chunk) event.getChunk();
        Key key = new Key((WorldServer) event.getWorld(), chunk.x, chunk.z);
        PENDING.remove(key);
        NBTTagCompound marker = event.getData().getCompound(TAG);
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
        if (!pending.cells.isEmpty()) { pending.recovery = true; PENDING.put(key, pending); }
    }

    /** Uses the caller's shared tick budget and only the chunks already available. */
    static int tick(long deadline, int maximumVisits) {
        int visits = 0;
        for (Map.Entry<Key, Pending> entry : PENDING.entrySet()) {
            if (System.nanoTime() >= deadline || visits >= maximumVisits) break;
            Key key = entry.getKey(); Pending pending = entry.getValue();
            if (!pending.recovery || AdminWorldAccess.loaded(key.world, key.x, key.z) != pending.chunk) continue;
            if (!key.world.isAreaLoaded(new BlockPos((key.x << 4) - 17, 0, (key.z << 4) - 17),
                    new BlockPos((key.x << 4) + 32, 255, (key.z << 4) + 32), false)) continue;
            synchronized (pending) {
                if (!pending.heightsReady) {
                    AdminWorldAccess.repairHeightmaps(pending.chunk);
                    pending.heightsReady = true;
                }
                for (int cell = pending.cells.nextSetBit(0); cell >= 0 && System.nanoTime() < deadline
                        && visits < maximumVisits; cell = pending.cells.nextSetBit(cell + 1)) {
                    BlockPos at = new BlockPos((key.x << 4) + (cell & 15), cell >>> 8,
                            (key.z << 4) + ((cell >>> 4) & 15));
                    if (!key.world.checkLight(at)) break;
                    pending.cells.clear(cell); visits++;
                }
                pending.chunk.markDirty();
                if (pending.cells.isEmpty()) PENDING.remove(key, pending);
            }
        }
        return visits;
    }
    public static void clear() { PENDING.clear(); }

    private static final class Pending {
        final Chunk chunk; final BitSet cells = new BitSet(65536);
        boolean recovery, heightsReady;
        Pending(Chunk chunk) { this.chunk = chunk; }
    }
    private static final class Key {
        final WorldServer world; final int x, z;
        Key(WorldServer world, int x, int z) { this.world = world; this.x = x; this.z = z; }
        @Override public int hashCode() { return (System.identityHashCode(world) * 31 + x) * 31 + z; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return world == other.world && x == other.x && z == other.z;
        }
    }
}
