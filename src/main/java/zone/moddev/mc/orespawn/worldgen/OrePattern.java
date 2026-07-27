package zone.moddev.mc.orespawn.worldgen;

import java.util.Locale;

/** Shapes supported by OreSpawn's allocation-free managed ore feature. */
public enum OrePattern {
	DEFAULT("default"),
	VEIN("vein"),
	NORMAL_CLOUD("normal_cloud"),
	PRECISION("precision"),
	CLUSTERS("clusters"),
	UNDERFLUIDS("underfluids");

	/** @deprecated Config and source compatibility alias. */
	@Deprecated
	public static final OrePattern CLUSTER = CLUSTERS;
	/** @deprecated Config and source compatibility alias. */
	@Deprecated
	public static final OrePattern CLOUD = NORMAL_CLOUD;

	public final String configName;

	OrePattern(String configName) {
		this.configName = configName;
	}

	public static OrePattern fromConfigName(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if ("cluster".equals(normalized)) {
			return CLUSTERS;
		}
		if ("cloud".equals(normalized) || "normal-cloud".equals(normalized)) {
			return NORMAL_CLOUD;
		}
		if ("under_fluid".equals(normalized) || "under-fluid".equals(normalized)) {
			return UNDERFLUIDS;
		}
		for (OrePattern pattern : values()) {
			if (pattern.configName.equals(normalized)) {
				return pattern;
			}
		}
		throw new IllegalArgumentException("Unknown ore pattern: " + value);
	}
}
