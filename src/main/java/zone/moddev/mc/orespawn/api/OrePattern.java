package zone.moddev.mc.orespawn.api;

/** Shapes supported by OreSpawn's bounded ore generator. */
public enum OrePattern {
	DEFAULT("default"),
	VEIN("vein"),
	NORMAL_CLOUD("normal_cloud"),
	PRECISION("precision"),
	CLUSTERS("clusters"),
	UNDERFLUIDS("underfluids");

	/** @deprecated Use {@link #CLUSTERS}. */
	@Deprecated
	public static final OrePattern CLUSTER = CLUSTERS;
	/** @deprecated Use {@link #NORMAL_CLOUD}. */
	@Deprecated
	public static final OrePattern CLOUD = NORMAL_CLOUD;

	private final String configName;

	OrePattern(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
