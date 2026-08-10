package zone.moddev.mc.orespawn;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Small bootstrap fallback; the JSON profile is the authoritative worldgen configuration. */
public final class OreSpawnConfig {
	private static volatile boolean placeTerrain = true;
	private static volatile GeologyMode geologyMode = GeologyMode.GEOME;
	private static volatile int geomeSize = 256;
	private static volatile double rockLayerNoise = 32.0D;
	private static volatile int layerThickness = 8;

	private OreSpawnConfig() {
	}

	/** Loads the small global fallback file. Per-world JSON remains authoritative. */
	public static synchronized void load(File file) {
		Configuration config = new Configuration(file);
		try {
			config.load();
			String category = "worldgen";
			placeTerrain = config.getBoolean("place_terrain", category, true,
					"Master switch for configured terrain replacement.");
			String mode = config.getString("fallback_geology_mode", category,
					GeologyMode.GEOME.name(), "Fallback geology mode.",
					new String[] { GeologyMode.GEOME.name(), GeologyMode.LEGACY.name() });
			try {
				geologyMode = GeologyMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				geologyMode = GeologyMode.GEOME;
			}
			geomeSize = config.getInt("cyano_region_size", category, 256, 4,
					Short.MAX_VALUE, "Fallback Cyano region size.");
			rockLayerNoise = config.getFloat("cyano_layer_reach", category, 32.0F,
					1.0F, Short.MAX_VALUE, "Fallback Cyano layer reach.");
			layerThickness = config.getInt("cyano_layer_thickness", category, 8, 1,
					255, "Fallback Cyano layer thickness.");
		} finally {
			if (config.hasChanged()) config.save();
		}
	}

	public static boolean placeOreSpawnRock() { return placeTerrain; }
	public static GeologyMode geologyMode() { return geologyMode; }
	public static int geomeSize() { return geomeSize; }
	public static double rockLayerNoise() { return rockLayerNoise; }
	public static int geomLayerThickness() { return layerThickness; }
	/** Legacy fallback input retained for source compatibility; JSON deposits are authoritative. */
	@Deprecated
	public static boolean placeCrudeOil() { return false; }

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
