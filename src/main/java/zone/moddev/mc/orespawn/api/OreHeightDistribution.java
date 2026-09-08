package zone.moddev.mc.orespawn.api;

/** Vertical distributions supported by OreSpawn-managed ores. */
public enum OreHeightDistribution {
	UNIFORM("uniform"),
	TRIANGLE("triangle"),
	BOTTOM_TRIANGLE("bottom_triangle"),
	UNIFORM_BOTTOM_TRIANGLE("uniform_bottom_triangle");

	private final String configName;

	OreHeightDistribution(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
