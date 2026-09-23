package zone.moddev.mc.orespawn.api;

import java.util.Optional;

import net.minecraft.util.ResourceLocation;

/**
 * Extended placement context supplied by OreSpawn 4.1 and later.
 *
 * <p>The identity values are stable for the current chunk in both ordinary
 * generation and supported retrogen. Patterns should combine them with their
 * own stable definition identifier and salt instead of relying on invocation
 * order or the mutable state of {@link #random()}.</p>
 *
 * <p>This is a secondary interface so implementations compiled against the
 * original {@link OrePlacementContext} contract remain binary compatible.</p>
 */
public interface OreGenerationContext extends OrePlacementContext {
	/** Stable seed of the world being generated. */
	long worldSeed();

	/** Stable namespaced identity of the dimension being generated. */
	ResourceLocation dimension();

	/** X coordinate of the current chunk, not its minimum block coordinate. */
	int chunkX();

	/** Z coordinate of the current chunk, not its minimum block coordinate. */
	int chunkZ();

	/**
	 * Returns the active allocation-light geology sampler when the dimension has
	 * an OreSpawn geology configuration. Pattern implementations should cache no
	 * world or implementation objects beyond the placement call.
	 */
	Optional<GeologySampler> geologySampler();
}
