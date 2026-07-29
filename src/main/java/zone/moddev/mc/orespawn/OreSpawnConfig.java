package zone.moddev.mc.orespawn;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/** Small bootstrap fallback; the JSON profile is the authoritative worldgen configuration. */
public final class OreSpawnConfig {
	private static final Common COMMON;
	private static final ModConfigSpec SPEC;

	private static volatile boolean placeTerrain = true;
	private static volatile GeologyMode geologyMode = GeologyMode.GEOME;
	private static volatile int geomeSize = 256;
	private static volatile double rockLayerNoise = 32.0D;
	private static volatile int layerThickness = 8;

	static {
		Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
		COMMON = pair.getLeft();
		SPEC = pair.getRight();
	}

	private OreSpawnConfig() {
	}

	public static void register(ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, SPEC, "orespawn-common.toml");
	}

	public static void bake() {
		placeTerrain = COMMON.placeTerrain.get();
		geologyMode = COMMON.geologyMode.get();
		geomeSize = COMMON.geomeSize.get();
		rockLayerNoise = COMMON.rockLayerNoise.get();
		layerThickness = COMMON.layerThickness.get();
	}

	public static boolean placeOreSpawnRock() { return placeTerrain; }
	public static GeologyMode geologyMode() { return geologyMode; }
	public static int geomeSize() { return geomeSize; }
	public static double rockLayerNoise() { return rockLayerNoise; }
	public static int geomLayerThickness() { return layerThickness; }
	/** Legacy fallback input retained for source compatibility; JSON deposits are authoritative. */
	@Deprecated
	public static boolean placeCrudeOil() { return false; }

	private static final class Common {
		final ModConfigSpec.BooleanValue placeTerrain;
		final ModConfigSpec.EnumValue<GeologyMode> geologyMode;
		final ModConfigSpec.IntValue geomeSize;
		final ModConfigSpec.DoubleValue rockLayerNoise;
		final ModConfigSpec.IntValue layerThickness;

		Common(ModConfigSpec.Builder builder) {
			builder.comment("Bootstrap fallbacks. Detailed settings live in orespawn-worldgen.json.")
					.push("worldgen");
			placeTerrain = builder.comment("Master switch for configured terrain replacement.")
					.define("place_terrain", true);
			geologyMode = builder.defineEnum("fallback_geology_mode", GeologyMode.GEOME);
			geomeSize = builder.defineInRange("cyano_region_size", 256, 4, Short.MAX_VALUE);
			rockLayerNoise = builder.defineInRange("cyano_layer_reach", 32.0D, 1.0D, Short.MAX_VALUE);
			layerThickness = builder.defineInRange("cyano_layer_thickness", 8, 1, 255);
			builder.pop();
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

	public enum GeologyMode {
		GEOME,
		LEGACY
	}
}
