package zone.moddev.mc.orespawn.worldgen.math;

public class PerlinNoise2D {

	private final NoiseLayer2D[] layers;

	/** from java.util.Random implementation */
	private static final long RAND_MULTIPLIER = 0x5DEECE66DL;
	/** from java.util.Random implementation */
	private static final long RAND_ADDEND = 0xBL;
	/** from java.util.Random implementation */
	private static final long RAND_MASK = (1L << 48) - 1;
	private static final int BROAD_STRATUM_LAYERS = 2;

	public PerlinNoise2D(long seed, float initialRange, float initialSize, int numOvertoneLayers) {
		layers = new NoiseLayer2D[numOvertoneLayers];
		for (int i = 0; i < layers.length; i++) {
			seed = scramble(seed);
			layers[i] = new NoiseLayer2D(seed, initialSize, initialRange);
			initialSize *= 0.5;
			initialRange *= 0.5;
		}
	}

	private PerlinNoise2D(NoiseLayer2D[] layers) {
		this.layers = layers;
	}

	public static PerlinNoise2D normalized(long seed, float totalRange, float initialSize,
			int numOvertoneLayers) {
		int layerCount = Math.max(1, numOvertoneLayers);
		return new PerlinNoise2D(seed, normalizedInitialRange(totalRange, layerCount), initialSize, layerCount);
	}

	// Keep the independent broad and fine bands in one array so sampling stays allocation-free and single-pass.
	public static PerlinNoise2D independentStrata(long seed, float wavinessRange, float wavinessSize,
			float edgeRange, float edgeSize, int edgeLayers) {
		int fineLayerCount = edgeRange <= 0.0F ? 0 : Math.max(0, edgeLayers);
		NoiseLayer2D[] layers = new NoiseLayer2D[BROAD_STRATUM_LAYERS + fineLayerCount];
		float range = normalizedInitialRange(wavinessRange, BROAD_STRATUM_LAYERS);
		float size = wavinessSize;
		for (int i = 0; i < BROAD_STRATUM_LAYERS; i++) {
			seed = scramble(seed);
			layers[i] = new NoiseLayer2D(seed, size, range);
			size *= 0.5F;
			range *= 0.5F;
		}

		range = fineLayerCount == 0 ? 0.0F : normalizedInitialRange(edgeRange, fineLayerCount);
		size = edgeSize;
		for (int i = 0; i < fineLayerCount; i++) {
			seed = scramble(seed);
			layers[BROAD_STRATUM_LAYERS + i] = new NoiseLayer2D(seed, size, range);
			size *= 0.5F;
			range *= 0.5F;
		}
		return new PerlinNoise2D(layers);
	}

	public float valueAt(double x, double y) {
		float sum = 0;
		for (int i = 0; i < layers.length; i++) {
			sum += layers[i].getValueAt(x, y);
		}
		return sum;
	}

	private static long scramble(long l) {
		return ((l * RAND_MULTIPLIER) + RAND_ADDEND) & RAND_MASK;
	}

	private static float normalizedInitialRange(float totalRange, int layerCount) {
		float weight = 0.0F;
		float layerWeight = 1.0F;
		for (int i = 0; i < layerCount; i++) {
			weight += layerWeight;
			layerWeight *= 0.5F;
		}
		return totalRange / weight;
	}
}
