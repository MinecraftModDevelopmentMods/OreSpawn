package com.mcmoddev.orespawn.client;

enum OreRichnessPreset {
	ULTRA_POOR("ultra_poor", 0.25D),
	POOR("poor", 0.5D),
	AVERAGE("average", 1.0D),
	RICH("rich", 2.0D),
	ULTRA_RICH("ultra_rich", 4.0D);

	static final double MAX_FREQUENCY = 64.0D;
	private static final double MATCH_TOLERANCE = 0.000001D;

	final String configName;
	private final double multiplier;

	OreRichnessPreset(String configName, double multiplier) {
		this.configName = configName;
		this.multiplier = multiplier;
	}

	double scaledFrequency(double baseline) {
		double safeBaseline = Double.isFinite(baseline) && baseline >= 0.0D ? baseline : 1.0D;
		return Math.min(MAX_FREQUENCY, safeBaseline * multiplier);
	}

	static OreRichnessPreset fromFrequency(double baseline, double frequency) {
		for (OreRichnessPreset preset : values()) {
			if (Math.abs(preset.scaledFrequency(baseline) - frequency) <= MATCH_TOLERANCE) {
				return preset;
			}
		}
		return AVERAGE;
	}
}
