package com.mcmoddev.orespawn.worldgen;

public final class FormationSettings {
	public enum Algorithm {
		STABLE_LAYERS("stable_layers"),
		SKY_V1("sky_v1");

		final String configName;

		Algorithm(String configName) {
			this.configName = configName;
		}

		public String configName() {
			return configName;
		}

		public static Algorithm fromConfigName(String name) {
			for (Algorithm algorithm : values()) {
				if (algorithm.configName.equalsIgnoreCase(name)) {
					return algorithm;
				}
			}
			throw new IllegalArgumentException("Unknown formation algorithm: " + name);
		}
	}

	public enum Preset {
		TINY("tiny", 64.0D, 25.0D, 128.0D, 1, 15.0D, 1, 0.00D, 12.0D, 32.0D, 0.0D, 0),
		SMALL("small", 128.0D, 50.0D, 192.0D, 3, 30.0D, 2, 0.50D, 24.0D, 48.0D, 4.0D, 1),
		AVERAGE("average", 256.0D, 100.0D, 256.0D, 8, 60.0D, 4, 0.85D, 48.0D, 64.0D, 12.0D, 2),
		LARGE("large", 512.0D, 200.0D, 384.0D, 28, 90.0D, 5, 0.95D, 128.0D, 96.0D, 24.0D, 3),
		HUGE("huge", 1024.0D, 640.0D, 512.0D, 128, 120.0D, 6, 1.00D, 288.0D, 128.0D, 48.0D, 4),
		CUSTOM("custom", 256.0D, 100.0D, 256.0D, 8, 60.0D, 4, 0.85D, 48.0D, 64.0D, 12.0D, 2);

		final String configName;
		final double stratumWavelength;
		final double familyRegionWavelength;
		final double stableWavinessWavelength;
		final int verticalThickness;
		final double wavinessAmplitude;
		final int edgeOctaves;
		final double continuity;
		final double stableWavinessAmplitude;
		final double stableEdgeWavelength;
		final double stableEdgeAmplitude;
		final int stableEdgeOctaves;

		Preset(String configName, double stratumWavelength, double familyRegionWavelength,
				double stableWavinessWavelength,
				int verticalThickness, double wavinessAmplitude, int edgeOctaves, double continuity,
				double stableWavinessAmplitude, double stableEdgeWavelength,
				double stableEdgeAmplitude, int stableEdgeOctaves) {
			this.configName = configName;
			this.stratumWavelength = stratumWavelength;
			this.familyRegionWavelength = familyRegionWavelength;
			this.stableWavinessWavelength = stableWavinessWavelength;
			this.verticalThickness = verticalThickness;
			this.wavinessAmplitude = wavinessAmplitude;
			this.edgeOctaves = edgeOctaves;
			this.continuity = continuity;
			this.stableWavinessAmplitude = stableWavinessAmplitude;
			this.stableEdgeWavelength = stableEdgeWavelength;
			this.stableEdgeAmplitude = stableEdgeAmplitude;
			this.stableEdgeOctaves = stableEdgeOctaves;
		}

		public String configName() {
			return configName;
		}

		public static Preset fromConfigName(String name) {
			for (Preset preset : values()) {
				if (preset.configName.equalsIgnoreCase(name)) {
					return preset;
				}
			}
			throw new IllegalArgumentException("Unknown formation preset: " + name);
		}
	}

	final Algorithm algorithm;
	final double stratumWavelength;
	final double familyRegionWavelength;
	final int verticalThickness;
	final double wavinessAmplitude;
	final double edgeWavelength;
	final double edgeAmplitude;
	final int edgeOctaves;
	final double continuity;

	FormationSettings(Algorithm algorithm, double stratumWavelength, double familyRegionWavelength,
			int verticalThickness, double wavinessAmplitude, double edgeWavelength,
			double edgeAmplitude, int edgeOctaves, double continuity) {
		this.algorithm = algorithm;
		this.stratumWavelength = stratumWavelength;
		this.familyRegionWavelength = familyRegionWavelength;
		this.verticalThickness = verticalThickness;
		this.wavinessAmplitude = wavinessAmplitude;
		this.edgeWavelength = edgeWavelength;
		this.edgeAmplitude = edgeAmplitude;
		this.edgeOctaves = edgeOctaves;
		this.continuity = continuity;
	}

	boolean usesStableLayers() {
		return algorithm == Algorithm.STABLE_LAYERS;
	}

	int familyDiversitySlots() {
		if (!usesStableLayers() || verticalThickness <= 16) {
			return 1;
		}
		return verticalThickness < 64 ? 2 : 4;
	}
}
