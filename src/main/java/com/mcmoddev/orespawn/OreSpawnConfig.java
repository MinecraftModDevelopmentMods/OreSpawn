package com.mcmoddev.orespawn;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Small bootstrap fallback; the JSON profile is the authoritative worldgen configuration. */
public final class OreSpawnConfig {
	private static final Common COMMON;
	private static final ForgeConfigSpec SPEC;

	private static volatile boolean placeTerrain = true;
	private static volatile GeologyMode geologyMode = GeologyMode.GEOME;
	private static volatile int geomeSize = 256;
	private static volatile double rockLayerNoise = 32.0D;
	private static volatile int layerThickness = 8;
	private static volatile boolean placeCrudeOil;
	private static volatile OilGenerationSettings crudeOil =
			new OilGenerationSettings(-48, 48, 0.08D, 5, 12, 2, 5, 4, 2);

	static {
		Pair<Common, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Common::new);
		COMMON = pair.getLeft();
		SPEC = pair.getRight();
	}

	private OreSpawnConfig() {
	}

	public static void register() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "orespawn-common.toml");
	}

	public static void bake() {
		placeTerrain = COMMON.placeTerrain.get();
		geologyMode = COMMON.geologyMode.get();
		geomeSize = COMMON.geomeSize.get();
		rockLayerNoise = COMMON.rockLayerNoise.get();
		layerThickness = COMMON.layerThickness.get();
		placeCrudeOil = COMMON.placeCrudeOil.get();
		crudeOil = new OilGenerationSettings(COMMON.oilMinY.get(), COMMON.oilMaxY.get(),
				COMMON.oilFrequency.get(), COMMON.oilMinRadius.get(), COMMON.oilMaxRadius.get(),
				COMMON.oilMinVerticalRadius.get(), COMMON.oilMaxVerticalRadius.get(),
				COMMON.oilMaxLobes.get(), COMMON.oilSolidCover.get());
	}

	public static boolean placeOreSpawnRock() { return placeTerrain; }
	public static GeologyMode geologyMode() { return geologyMode; }
	public static int geomeSize() { return geomeSize; }
	public static double rockLayerNoise() { return rockLayerNoise; }
	public static int geomLayerThickness() { return layerThickness; }
	public static boolean placeCrudeOil() { return placeCrudeOil; }
	public static OilGenerationSettings crudeOil() { return crudeOil; }

	private static final class Common {
		final ForgeConfigSpec.BooleanValue placeTerrain;
		final ForgeConfigSpec.EnumValue<GeologyMode> geologyMode;
		final ForgeConfigSpec.IntValue geomeSize;
		final ForgeConfigSpec.DoubleValue rockLayerNoise;
		final ForgeConfigSpec.IntValue layerThickness;
		final ForgeConfigSpec.BooleanValue placeCrudeOil;
		final ForgeConfigSpec.IntValue oilMinY;
		final ForgeConfigSpec.IntValue oilMaxY;
		final ForgeConfigSpec.DoubleValue oilFrequency;
		final ForgeConfigSpec.IntValue oilMinRadius;
		final ForgeConfigSpec.IntValue oilMaxRadius;
		final ForgeConfigSpec.IntValue oilMinVerticalRadius;
		final ForgeConfigSpec.IntValue oilMaxVerticalRadius;
		final ForgeConfigSpec.IntValue oilMaxLobes;
		final ForgeConfigSpec.IntValue oilSolidCover;

		Common(ForgeConfigSpec.Builder builder) {
			builder.comment("Bootstrap fallbacks. Detailed settings live in orespawn-worldgen.json.")
					.push("worldgen");
			placeTerrain = builder.comment("Master switch for configured terrain replacement.")
					.define("place_terrain", true);
			geologyMode = builder.defineEnum("fallback_geology_mode", GeologyMode.GEOME);
			geomeSize = builder.defineInRange("cyano_region_size", 256, 4, Short.MAX_VALUE);
			rockLayerNoise = builder.defineInRange("cyano_layer_reach", 32.0D, 1.0D, Short.MAX_VALUE);
			layerThickness = builder.defineInRange("cyano_layer_thickness", 8, 1, 255);
			placeCrudeOil = builder.define("fallback_place_crude_oil", false);
			builder.push("oil_fallback");
			oilMinY = builder.defineInRange("min_y", -48, -2048, 2048);
			oilMaxY = builder.defineInRange("max_y", 48, -2048, 2048);
			oilFrequency = builder.defineInRange("frequency", 0.08D, 0.0D, 64.0D);
			oilMinRadius = builder.defineInRange("min_radius", 5, 1, 64);
			oilMaxRadius = builder.defineInRange("max_radius", 12, 1, 64);
			oilMinVerticalRadius = builder.defineInRange("min_vertical_radius", 2, 1, 32);
			oilMaxVerticalRadius = builder.defineInRange("max_vertical_radius", 5, 1, 32);
			oilMaxLobes = builder.defineInRange("max_lobes", 4, 1, 16);
			oilSolidCover = builder.defineInRange("min_solid_cover", 2, 1, 64);
			builder.pop(2);
		}
	}

	public static final class OreGenerationSettings {
		private final int minY;
		private final int maxY;
		private final double frequency;
		private final int quantity;
		public OreGenerationSettings(int minY, int maxY, double frequency, int quantity) {
			this.minY = minY; this.maxY = maxY; this.frequency = frequency; this.quantity = quantity;
		}
		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double frequency() { return frequency; }
		public int quantity() { return quantity; }
	}

	public static final class OilGenerationSettings {
		private final int minY;
		private final int maxY;
		private final double frequency;
		private final int minRadius;
		private final int maxRadius;
		private final int minVerticalRadius;
		private final int maxVerticalRadius;
		private final int maxLobes;
		private final int minSolidCover;
		public OilGenerationSettings(int minY, int maxY, double frequency, int minRadius, int maxRadius,
				int minVerticalRadius, int maxVerticalRadius, int maxLobes, int minSolidCover) {
			this.minY = minY; this.maxY = maxY; this.frequency = frequency;
			this.minRadius = Math.min(minRadius, maxRadius); this.maxRadius = Math.max(minRadius, maxRadius);
			this.minVerticalRadius = Math.min(minVerticalRadius, maxVerticalRadius);
			this.maxVerticalRadius = Math.max(minVerticalRadius, maxVerticalRadius);
			this.maxLobes = maxLobes; this.minSolidCover = minSolidCover;
		}
		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double frequency() { return frequency; }
		public int minRadius() { return minRadius; }
		public int maxRadius() { return maxRadius; }
		public int minVerticalRadius() { return minVerticalRadius; }
		public int maxVerticalRadius() { return maxVerticalRadius; }
		public int maxLobes() { return maxLobes; }
		public int minSolidCover() { return minSolidCover; }
	}

	public enum GeologyMode {
		GEOME,
		LEGACY
	}
}
