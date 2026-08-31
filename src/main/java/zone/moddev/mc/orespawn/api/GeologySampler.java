package zone.moddev.mc.orespawn.api;

/** Reusable, read-only sampler for the active world's baked geology. */
public interface GeologySampler {
	/**
	 * Classifies one column. The returned column reuses that biome/geome
	 * classification for all subsequent Y queries. {@code surfaceY} is the first
	 * free block returned by {@code Level.getHeight}; OreSpawn classifies the
	 * stable quart biome at the highest occupied block, matching chunk geology
	 * generation without Minecraft's display-oriented fuzzy biome zoom.
	 */
	GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY);
}
