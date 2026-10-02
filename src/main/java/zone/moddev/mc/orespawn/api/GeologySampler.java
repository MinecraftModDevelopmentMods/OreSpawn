package zone.moddev.mc.orespawn.api;

/** Reusable, read-only sampler for the active world's baked geology. */
public interface GeologySampler {
	/**
	 * Classifies a column once, then reuses its biome and geome for every height query.
	 */
	GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY);
}
