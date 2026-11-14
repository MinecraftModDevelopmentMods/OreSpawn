package zone.moddev.mc.orespawn.worldgen;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Updates loaded chunks in small batches and retains the revision each chunk actually completed. */
public final class OreRetrogenManager {
	private static final String ROOT_TAG = "OreSpawn";
	private static final String REVISION_TAG = "generation_revision";
	private static final ConcurrentLinkedQueue<QueuedChunk> QUEUE = new ConcurrentLinkedQueue<>();
	private static final Set<ChunkKey> QUEUED = ConcurrentHashMap.newKeySet();
	private static final Map<ChunkKey, Integer> COMPLETE = new ConcurrentHashMap<>();
	private static final Set<ChunkKey> GENERATED_THIS_SESSION = ConcurrentHashMap.newKeySet();
	private static volatile Settings settings = Settings.DISABLED;

	private OreRetrogenManager() {
	}

	public static void refreshWorldConfig() {
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		settings = new Settings(profile.oreRetrogenEnabled(), profile.forceOreRetrogen(),
				profile.flatBedrockRetrogenEnabled(), profile.retrogenChunksPerTick(),
				profile.generationRevision());
	}

	public static void onChunkLoad(ChunkDataEvent.Load event) {
		Settings current = settings;
		if (!(event.getWorld() instanceof WorldServer)
				|| !(event.getChunk() instanceof Chunk)) return;
		WorldServer level = (WorldServer) event.getWorld();
		Chunk chunk = (Chunk) event.getChunk();
		ChunkKey key = new ChunkKey(level, chunkKey(chunk.getPos()));
		NBTTagCompound marker = event.getData().getCompound(ROOT_TAG);
		Integer revision = marker.contains(REVISION_TAG, 3) && marker.getInt(REVISION_TAG) >= 0
				? marker.getInt(REVISION_TAG) : null;
		// Minecraft builds fresh NBT on every save, so keep loaded markers even while retrogen is off.
		if (revision == null) COMPLETE.remove(key);
		else COMPLETE.put(key, revision);
		if ((!current.oreEnabled && !current.bedrockEnabled)
				|| (!current.force && revision != null && revision == current.revision)) return;
		enqueue(level, chunk, false);
	}

	public static void onChunkSave(ChunkDataEvent.Save event) {
		if (!(event.getWorld() instanceof WorldServer)) return;
		WorldServer level = (WorldServer) event.getWorld();
		ChunkKey key = new ChunkKey(level, chunkKey(event.getChunk().getPos()));
		Integer revision = COMPLETE.get(key);
		if (revision == null) return;
		NBTTagCompound marker = event.getData().getCompound(ROOT_TAG);
		marker.setInt(REVISION_TAG, revision);
		event.getData().setTag(ROOT_TAG, marker);
	}

	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		for (int i = 0; i < settings.chunksPerTick; i++) {
			QueuedChunk queued = QUEUE.poll();
			if (queued == null) return;
			try {
				Settings current = settings;
				if (!current.oreEnabled && !current.bedrockEnabled) continue;
				ChunkPos queuedPos = queued.chunk.getPos();
				if (loadedChunk(queued.level, queuedPos.x, queuedPos.z) != queued.chunk) continue;
				Integer revision = COMPLETE.get(queued.key);
				if (!queued.explicit && !current.force && revision != null && revision == current.revision) continue;
				if (current.oreEnabled) OreSpawnOreGeneration.retrogen(queued.level, queued.chunk);
				if (current.bedrockEnabled) FlatBedrockFeature.flattenChunk(queued.level, queued.chunk);
				COMPLETE.put(queued.key, current.revision);
				GENERATED_THIS_SESSION.add(queued.key);
				queued.chunk.setModified(true);
			} finally {
				QUEUED.remove(queued.key);
			}
		}
	}

	static void markGenerated(WorldServer level, ChunkPos chunk) {
		ChunkKey key = new ChunkKey(level, chunkKey(chunk));
		COMPLETE.put(key, settings.revision);
		GENERATED_THIS_SESSION.add(key);
	}

	static boolean generatedThisSession(WorldServer level, ChunkPos chunk) {
		return GENERATED_THIS_SESSION.contains(new ChunkKey(level, chunkKey(chunk)));
	}

	public static int queueLoadedArea(WorldServer level, ChunkPos center, int radius) {
		int count = 0;
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				Chunk chunk = loadedChunk(level, x, z);
				if (chunk != null && enqueue(level, chunk, true)) count++;
			}
		}
		return count;
	}

	public static int queuedCount() {
		return QUEUE.size();
	}

	/** Session bookkeeping only; this does not load NBT, enqueue work or write a marker. */
	public static com.google.gson.JsonObject diagnosticStatus(WorldServer level, ChunkPos position) {
		Settings current = settings;
		ChunkKey key = new ChunkKey(level, chunkKey(position));
		com.google.gson.JsonObject result = new com.google.gson.JsonObject();
		result.addProperty("enabled", current.oreEnabled);
		result.addProperty("bedrock_enabled", current.bedrockEnabled);
		result.addProperty("force", current.force);
		result.addProperty("configured_revision", current.revision);
		result.addProperty("chunks_per_tick", current.chunksPerTick);
		result.addProperty("queue_size", QUEUE.size());
		result.addProperty("chunk_queued", QUEUED.contains(key));
		result.addProperty("chunk_generation_completed_this_session", GENERATED_THIS_SESSION.contains(key));
		result.addProperty("persisted_marker", "unknown: not read by this command");
		return result;
	}


	public static void clear() {
		QUEUE.clear();
		QUEUED.clear();
		COMPLETE.clear();
		GENERATED_THIS_SESSION.clear();
		settings = Settings.DISABLED;
	}

	private static boolean enqueue(WorldServer level, Chunk chunk, boolean explicit) {
		ChunkKey key = new ChunkKey(level, chunkKey(chunk.getPos()));
		if (!QUEUED.add(key)) return false;
		QUEUE.add(new QueuedChunk(level, chunk, key, explicit));
		return true;
	}

	private static Chunk loadedChunk(WorldServer level, int x, int z) {
        net.minecraft.world.chunk.IChunk loaded = level.getChunkProvider().chunkExists(x, z)
                ? level.getChunkProvider().provideChunk(x, z, false, false) : null;
        return loaded instanceof Chunk ? (Chunk) loaded : null;
    }

	private static long chunkKey(ChunkPos pos) {
		return ((long) pos.x & 0xFFFFFFFFL) | (((long) pos.z & 0xFFFFFFFFL) << 32);
	}


	private static final class Settings {
		static final Settings DISABLED = new Settings(false, false, false, 1, 0);
		final boolean oreEnabled;
		final boolean force;
		final boolean bedrockEnabled;
		final int chunksPerTick;
		final int revision;
		Settings(boolean oreEnabled, boolean force, boolean bedrockEnabled,
				int chunksPerTick, int revision) {
			this.oreEnabled = oreEnabled;
			this.force = force;
			this.bedrockEnabled = bedrockEnabled;
			this.chunksPerTick = chunksPerTick;
			this.revision = revision;
		}
	}

	private static final class QueuedChunk {
		final WorldServer level;
		final Chunk chunk;
		final ChunkKey key;
		final boolean explicit;
		QueuedChunk(WorldServer level, Chunk chunk, ChunkKey key, boolean explicit) {
			this.level = level;
			this.chunk = chunk;
			this.key = key;
			this.explicit = explicit;
		}
	}

	private static final class ChunkKey {
		final WorldServer level;
		final long position;
		ChunkKey(WorldServer level, long position) {
			this.level = level;
			this.position = position;
		}
		@Override public int hashCode() { return (31 * System.identityHashCode(level)) + Long.hashCode(position); }
		@Override public boolean equals(Object value) {
			if (this == value) return true;
			if (!(value instanceof ChunkKey)) return false;
			ChunkKey other = (ChunkKey) value;
			return position == other.position && level == other.level;
		}
	}
}
