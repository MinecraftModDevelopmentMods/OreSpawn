package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.util.ResourceLocation;

/**
 * Extended placement context supplied by OreSpawn 4.1 and later.
 *
 * <p>These identities are stable for the current chunk during normal generation and supported retrogen.
 * Combine them with the pattern's own stable definition ID and salt; do not rely on invocation order
 * or the mutable state of {@link #random()}.</p>
 *
 * <p>The separate interface keeps implementations compiled against {@link OrePlacementContext}
 * binary compatible.</p>
 */
public interface OreGenerationContext extends OrePlacementContext {
	/**
	 * Attempts placement with the same stable identity for every slice of one deposit.
	 * Implementations compiled before this overload retain their original placement behaviour.
	 */
	default boolean tryPlace(int x, int y, int z, long outputIdentity) {
		return tryPlace(x, y, z);
	}

	/** Stable seed of the world being generated. */
	long worldSeed();

	/** Stable namespaced identity of the dimension being generated. */
	ResourceLocation dimension();

	/** X coordinate of the current chunk, not its minimum block coordinate. */
	int chunkX();

	/** Z coordinate of the current chunk, not its minimum block coordinate. */
	int chunkZ();

	/**
	 * Returns the geology sampler if the dimension has an OreSpawn geology configuration.
	 * Do not retain the sampler or world objects after the placement call.
	 */
	Optional<GeologySampler> geologySampler();
}
