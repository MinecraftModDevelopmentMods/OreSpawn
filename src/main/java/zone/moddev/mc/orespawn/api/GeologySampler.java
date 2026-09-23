package zone.moddev.mc.orespawn.api;

/** Reusable, read-only sampler for the active world's baked geology. */
public interface GeologySampler {
	/**
	 * Classifies one column. The returned column reuses that biome/geome
	 * classification for all subsequent Y queries. Sampling is read-only and
	 * does not load or generate the chunk containing the requested column.
	 */
	GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY);
}
