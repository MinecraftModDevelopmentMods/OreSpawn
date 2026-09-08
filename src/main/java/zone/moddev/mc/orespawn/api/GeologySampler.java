package zone.moddev.mc.orespawn.api;

/** Reusable, read-only sampler for the active world's baked geology. */
public interface GeologySampler {
	/**
	 * Classifies one column. The returned column reuses that biome/geome
	 * classification for all subsequent Y queries.
	 */
	GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY);
}
