package com.mcmoddev.orespawn.worldgen;

import java.util.Locale;

/** Height samplers used by managed ore placement rules. */
public enum OreHeightDistribution {
	UNIFORM("uniform"),
	TRIANGLE("triangle");

	public final String configName;

	OreHeightDistribution(String configName) {
		this.configName = configName;
	}

	public static OreHeightDistribution fromConfigName(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		for (OreHeightDistribution distribution : values()) {
			if (distribution.configName.equals(normalized)) {
				return distribution;
			}
		}
		throw new IllegalArgumentException("Unknown ore height distribution: " + value);
	}
}
