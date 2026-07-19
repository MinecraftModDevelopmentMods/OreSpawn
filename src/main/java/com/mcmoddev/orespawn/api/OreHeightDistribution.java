package com.mcmoddev.orespawn.api;

/** Vertical distributions supported by OreSpawn-managed ores. */
public enum OreHeightDistribution {
	UNIFORM("uniform"),
	TRIANGLE("triangle");

	private final String configName;

	OreHeightDistribution(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
