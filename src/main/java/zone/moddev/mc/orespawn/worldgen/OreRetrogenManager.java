package zone.moddev.mc.orespawn.worldgen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded, marker-based ore and flat-bedrock retrogen with no reflected internals. */
public final class OreRetrogenManager {
	private static final String ROOT_TAG = "OreSpawn";
	private static final String REVISION_TAG = "generation_revision";
	private static final ConcurrentLinkedQueue<QueuedChunk> QUEUE = new ConcurrentLinkedQueue<>();
	private static final Set<ChunkKey> QUEUED = ConcurrentHashMap.newKeySet();
	private static final Set<ChunkKey> COMPLETE = ConcurrentHashMap.newKeySet();
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
		if ((!current.oreEnabled && !current.bedrockEnabled)
				|| event.getType() != ChunkType.LEVELCHUNK
				|| !(event.getLevel() instanceof ServerLevel)
				|| !(event.getChunk() instanceof LevelChunk)) return;
		ServerLevel level = (ServerLevel) event.getLevel();
		LevelChunk chunk = (LevelChunk) event.getChunk();
		CompoundTag marker = event.getData().structureData().getCompoundOrEmpty(ROOT_TAG);
		if (!current.force && marker.getIntOr(REVISION_TAG, Integer.MIN_VALUE) == current.revision) return;
		enqueue(level, chunk);
	}

	public static void onChunkSave(ChunkDataEvent.Save event) {
		if (!(event.getLevel() instanceof ServerLevel)) return;
		ServerLevel level = (ServerLevel) event.getLevel();
		ChunkKey key = new ChunkKey(level.dimension(), event.getChunk().getPos().toLong());
		if (!COMPLETE.contains(key)) return;
		CompoundTag marker = event.getData().structureData().getCompoundOrEmpty(ROOT_TAG);
		marker.putInt(REVISION_TAG, settings.revision);
		event.getData().structureData().put(ROOT_TAG, marker);
	}

	public static void onServerTick(ServerTickEvent.Post event) {
		for (int i = 0; i < settings.chunksPerTick; i++) {
			QueuedChunk queued = QUEUE.poll();
			if (queued == null) return;
			QUEUED.remove(queued.key);
			if (queued.level.getChunkSource().getChunkNow(
					queued.chunk.getPos().x, queued.chunk.getPos().z) != queued.chunk) continue;
			Settings current = settings;
			if (current.oreEnabled) OreSpawnOreGeneration.retrogen(queued.level, queued.chunk);
			if (current.bedrockEnabled) FlatBedrockFeature.flattenChunk(queued.level, queued.chunk);
			queued.chunk.markUnsaved();
			COMPLETE.add(queued.key);
		}
	}

	static void markGenerated(ResourceKey<Level> dimension, ChunkPos chunk) {
		COMPLETE.add(new ChunkKey(dimension, chunk.toLong()));
	}

	public static int queueLoadedArea(ServerLevel level, ChunkPos center, int radius) {
		int count = 0;
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
				if (chunk != null && enqueue(level, chunk)) count++;
			}
		}
		return count;
	}

	public static int queuedCount() {
		return QUEUE.size();
	}

	public static void clear() {
		QUEUE.clear();
		QUEUED.clear();
		COMPLETE.clear();
		settings = Settings.DISABLED;
	}

	private static boolean enqueue(ServerLevel level, LevelChunk chunk) {
		ChunkKey key = new ChunkKey(level.dimension(), chunk.getPos().toLong());
		if (!QUEUED.add(key)) return false;
		QUEUE.add(new QueuedChunk(level, chunk, key));
		return true;
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
		final ServerLevel level;
		final LevelChunk chunk;
		final ChunkKey key;
		QueuedChunk(ServerLevel level, LevelChunk chunk, ChunkKey key) {
			this.level = level;
			this.chunk = chunk;
			this.key = key;
		}
	}

	private static final class ChunkKey {
		final ResourceKey<Level> dimension;
		final long position;
		ChunkKey(ResourceKey<Level> dimension, long position) {
			this.dimension = dimension;
			this.position = position;
		}
		@Override public int hashCode() { return (31 * dimension.hashCode()) + Long.hashCode(position); }
		@Override public boolean equals(Object value) {
			if (this == value) return true;
			if (!(value instanceof ChunkKey)) return false;
			ChunkKey other = (ChunkKey) value;
			return position == other.position && dimension.equals(other.dimension);
		}
	}
}
