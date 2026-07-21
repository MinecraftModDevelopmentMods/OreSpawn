package com.mcmoddev.orespawn.worldgen;

import java.util.Locale;
import java.util.Random;

/** Height samplers used by managed ore placement rules. */
public enum OreHeightDistribution {
	UNIFORM("uniform"),
	TRIANGLE("triangle"),
	BOTTOM_TRIANGLE("bottom_triangle"),
	UNIFORM_BOTTOM_TRIANGLE("uniform_bottom_triangle");

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

	int sample(Random random, int min, int max) {
		int range = (max - min) + 1;
		switch (this) {
		case TRIANGLE:
			return min + ((random.nextInt(range) + random.nextInt(range)) / 2);
		case BOTTOM_TRIANGLE:
			return min + Math.min(random.nextInt(range), random.nextInt(range));
		case UNIFORM_BOTTOM_TRIANGLE:
			return random.nextBoolean()
					? min + random.nextInt(range)
					: min + Math.min(random.nextInt(bottomBand(range)), random.nextInt(bottomBand(range)));
		case UNIFORM:
		default:
			return min + random.nextInt(range);
		}
	}

	private static int bottomBand(int range) {
		// Vanilla's low redstone placement mixes a full-range pass with a
		// triangular pass concentrated in the bottom half.
		return Math.max(1, (range + 1) / 2);
	}
}
