package com.mcmoddev.orespawn.worldgen.math;

/**
 * Perlin Noise Octave
 *
 * @author Cyanobacterium
 *
 */
public class NoiseLayer2D {

	private final long seed;

	/** from java.util.Random implementation */
	private static final long RAND_MULTIPLIER = 0x5DEECE66DL;
	/** from java.util.Random implementation */
	private static final long RAND_ADDEND = 0xBL;
	/** from java.util.Random implementation */
	private static final long RAND_MASK = (1L << 48) - 1;

	private final float multiplier;

	private final float magnitude;

	private static final int PRECISION_MASK = 0x0FFFFF;
	private static final float INT_CONVERSION_MULTIPLIER = 2.0f / (float) PRECISION_MASK; // 2.0 because we will be subtracting 1
																				// to make it range from -1 to 1

	public NoiseLayer2D(long seed, float size, float magnitude) {
		this.seed = seed;
		this.multiplier = 1.0f / size;
		this.magnitude = magnitude;
	}

	public float getValueAt(double x, double y) {
		x *= multiplier;
		y *= multiplier;
		int gridX = CubicInterpolator.floor(x);
		int gridY = CubicInterpolator.floor(y);
		float sx0 = interpolateY(y, gridX, gridY, 0);
		float sx1 = interpolateY(y, gridX, gridY, 1);
		float sx2 = interpolateY(y, gridX, gridY, 2);
		float sx3 = interpolateY(y, gridX, gridY, 3);
		return CubicInterpolator.interpolate1d(x, sx0, sx1, sx2, sx3);
	}

	private float interpolateY(double y, int gridX, int gridY, int dx) {
		int sampleX = gridX + dx;
		return CubicInterpolator.interpolate1d(y,
				randAt(sampleX, gridY),
				randAt(sampleX, gridY + 1),
				randAt(sampleX, gridY + 2),
				randAt(sampleX, gridY + 3));
	}

	protected float randAt(int x, int y) {
		long h = hash(x, y, seed);
		return magnitude * ((h & PRECISION_MASK) * INT_CONVERSION_MULTIPLIER - 1.0f);
	}

	static long hash(int x, int y, long seed) {
		long l = (seed ^ RAND_MULTIPLIER) & RAND_MASK;
		l = (((l * RAND_MULTIPLIER) + RAND_ADDEND) ^ x) & RAND_MASK;
		l = (((l * RAND_MULTIPLIER) + RAND_ADDEND) ^ y) & RAND_MASK;
		l = ((l * RAND_MULTIPLIER) + RAND_ADDEND) & RAND_MASK;
		return l;
	}
}
