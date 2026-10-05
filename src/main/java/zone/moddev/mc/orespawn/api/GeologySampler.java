package zone.moddev.mc.orespawn.api;

/** Reusable, read-only sampler for the active world's baked geology. */
public interface GeologySampler {
	/**
	 * Classifies a column once, then reuses its biome and geome for every height query.
	 * Pass the first free height as {@code surfaceY}. OreSpawn samples the stable quart biome
	 * at the highest occupied block, matching generation rather than Minecraft's fuzzy display lookup.
	 */
	GeologyColumn sampleColumn(int blockX, int blockZ, int surfaceY);
}
