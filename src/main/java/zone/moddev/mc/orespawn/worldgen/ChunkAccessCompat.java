package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

/** Small, target-native adapter for Forge 1.10's pre-{@code getPos} chunk API. */
final class ChunkAccessCompat {
	private ChunkAccessCompat() {
	}

	static ChunkPos position(Chunk chunk) {
		return new ChunkPos(chunk.xPosition, chunk.zPosition);
	}

	static void markChanged(Chunk chunk) {
		chunk.setChunkModified();
	}
}
