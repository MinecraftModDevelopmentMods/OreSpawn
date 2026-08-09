package zone.moddev.mc.orespawn.worldgen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.TickEvent;
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
				|| event.getStatus() != ChunkStatus.Type.LEVELCHUNK
				|| !(event.getWorld() instanceof ServerWorld)
				|| !(event.getChunk() instanceof Chunk)) return;
		ServerWorld level = (ServerWorld) event.getWorld();
		Chunk chunk = (Chunk) event.getChunk();
		CompoundNBT marker = event.getData().getCompound(ROOT_TAG);
		if (!current.force && marker.getInt(REVISION_TAG) == current.revision) return;
		enqueue(level, chunk);
	}

	public static void onChunkSave(ChunkDataEvent.Save event) {
		if (!(event.getWorld() instanceof ServerWorld)) return;
		ServerWorld level = (ServerWorld) event.getWorld();
		ChunkKey key = new ChunkKey(WorldIds.dimension(level), event.getChunk().getPos().asLong());
		if (!COMPLETE.contains(key)) return;
		CompoundNBT marker = event.getData().getCompound(ROOT_TAG);
		marker.putInt(REVISION_TAG, settings.revision);
		event.getData().put(ROOT_TAG, marker);
	}

	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		for (int i = 0; i < settings.chunksPerTick; i++) {
			QueuedChunk queued = QUEUE.poll();
			if (queued == null) return;
			QUEUED.remove(queued.key);
			if (queued.level.getChunkProvider().getChunkWithoutLoading(
					queued.chunk.getPos().x, queued.chunk.getPos().z) != queued.chunk) continue;
			Settings current = settings;
			if (current.oreEnabled) OreSpawnOreGeneration.retrogen(queued.level, queued.chunk);
			if (current.bedrockEnabled) FlatBedrockFeature.flattenChunk(queued.level, queued.chunk);
			queued.chunk.setModified(true);
			COMPLETE.add(queued.key);
		}
	}

	static void markGenerated(ResourceLocation dimension, ChunkPos chunk) {
		COMPLETE.add(new ChunkKey(dimension, chunk.asLong()));
	}

	public static int queueLoadedArea(ServerWorld level, ChunkPos center, int radius) {
		int count = 0;
		for (int x = center.x - radius; x <= center.x + radius; x++) {
			for (int z = center.z - radius; z <= center.z + radius; z++) {
				Chunk chunk = level.getChunkProvider().getChunkWithoutLoading(x, z);
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

	private static boolean enqueue(ServerWorld level, Chunk chunk) {
		ChunkKey key = new ChunkKey(WorldIds.dimension(level), chunk.getPos().asLong());
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
		final ServerWorld level;
		final Chunk chunk;
		final ChunkKey key;
		QueuedChunk(ServerWorld level, Chunk chunk, ChunkKey key) {
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
