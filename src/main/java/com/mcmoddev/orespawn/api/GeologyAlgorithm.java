package com.mcmoddev.orespawn.api;

/** Supported terrain formation algorithms. */
public enum GeologyAlgorithm {
	STABLE_LAYERS("stable_layers"),
	SKY_V1("sky_v1");

	private final String configName;

	GeologyAlgorithm(String configName) {
		this.configName = configName;
	}

	public String configName() {
		return configName;
	}
}
