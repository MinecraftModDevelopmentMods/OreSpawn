package zone.moddev.mc.orespawn.worldgen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.ChunkDataEvent;

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
				|| !(event.getWorld() instanceof WorldServer)
				|| !(event.getChunk() instanceof Chunk)) return;
		WorldServer level = (WorldServer) event.getWorld();
		Chunk chunk = (Chunk) event.getChunk();
		NBTTagCompound marker = event.getData().getCompoundTag(ROOT_TAG);
		if (!current.force && marker.getInteger(REVISION_TAG) == current.revision) return;
		enqueue(level, chunk);
	}

	public static void onChunkSave(ChunkDataEvent.Save event) {
		if (!(event.getWorld() instanceof WorldServer)) return;
		WorldServer level = (WorldServer) event.getWorld();
		ChunkKey key = new ChunkKey(WorldIds.dimension(level), chunkKey(ChunkAccessCompat.position(event.getChunk())));
		if (!COMPLETE.contains(key)) return;
		NBTTagCompound marker = event.getData().getCompoundTag(ROOT_TAG);
		marker.setInteger(REVISION_TAG, settings.revision);
		event.getData().setTag(ROOT_TAG, marker);
	}

	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		for (int i = 0; i < settings.chunksPerTick; i++) {
			QueuedChunk queued = QUEUE.poll();
			if (queued == null) return;
			QUEUED.remove(queued.key);
			ChunkPos queuedPos = ChunkAccessCompat.position(queued.chunk);
			if (queued.level.getChunkProvider().getLoadedChunk(queuedPos.chunkXPos,
					queuedPos.chunkZPos) != queued.chunk) continue;
			Settings current = settings;
			if (current.oreEnabled) OreSpawnOreGeneration.retrogen(queued.level, queued.chunk);
			if (current.bedrockEnabled) FlatBedrockFeature.flattenChunk(queued.level, queued.chunk);
			queued.chunk.setModified(true);
			COMPLETE.add(queued.key);
		}
	}

	static void markGenerated(ResourceLocation dimension, ChunkPos chunk) {
		COMPLETE.add(new ChunkKey(dimension, chunkKey(chunk)));
	}

	public static int queueLoadedArea(WorldServer level, ChunkPos center, int radius) {
		int count = 0;
		for (int x = center.chunkXPos - radius; x <= center.chunkXPos + radius; x++) {
			for (int z = center.chunkZPos - radius; z <= center.chunkZPos + radius; z++) {
				Chunk loaded = level.getChunkProvider().getLoadedChunk(x, z);
				if (loaded != null && enqueue(level, loaded)) count++;
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

	private static boolean enqueue(WorldServer level, Chunk chunk) {
		ChunkKey key = new ChunkKey(WorldIds.dimension(level), chunkKey(ChunkAccessCompat.position(chunk)));
		if (!QUEUED.add(key)) return false;
		QUEUE.add(new QueuedChunk(level, chunk, key));
		return true;
	}

	private static long chunkKey(ChunkPos pos) {
		return ((long) pos.chunkXPos & 0xFFFFFFFFL) | (((long) pos.chunkZPos & 0xFFFFFFFFL) << 32);
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
		QueuedChunk(WorldServer level, Chunk chunk, ChunkKey key) {
			this.level = level;
			this.chunk = chunk;
			this.key = key;
		}
	}

	private static final class ChunkKey {
		final ResourceLocation dimension;
		final long position;
		ChunkKey(ResourceLocation dimension, long position) {
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
