package zone.moddev.mc.orespawn.worldgen.math;

/**
 * Cubic interpolation helpers for one-, two- and three-dimensional grids.
 *
 * @author Cyanobacterium
 */
public abstract class CubicInterpolator {

	/**
	 * Interpolates between the two middle samples of a four-point cubic curve.
	 *
	 * @param x x coordinate to interpolate
	 * @param yn2 sample at f(floor(x) - 1)
	 * @param yn1 sample at f(floor(x))
	 * @param yp1 sample at f(floor(x) + 1)
	 * @param yp2 sample at f(floor(x) + 2)
	 * @return the interpolated value
	 */
	public static double interpolate(double x, double yn2, double yn1, double yp1, double yp2) {
		return interpolate1d(x, yn2, yn1, yp1, yp2);
	}

	/**
	 * Interpolates between the two middle samples of a four-point cubic curve.
	 *
	 * @param x x coordinate to interpolate
	 * @param yn2 sample at f(floor(x) - 1)
	 * @param yn1 sample at f(floor(x))
	 * @param yp1 sample at f(floor(x) + 1)
	 * @param yp2 sample at f(floor(x) + 2)
	 * @return the interpolated value
	 */
	public static double interpolate1d(double x, double yn2, double yn1, double yp1, double yp2) {
		double w = x - Math.floor(x);
		if (w == 0 && x != 0)
			return yp1; // Snap near the endpoints to avoid floating-point artifacts.
		if (w < 0.000976563) {
			return yn1;
		}
		if (w > 0.999023438) {
			return yp1;
		}
		// Cubic coefficients adapted from http://www.paulinternet.nl/?page=bicubic
		double A = -0.5 * yn2 + 1.5 * yn1 - 1.5 * yp1 + 0.5 * yp2;
		double B = yn2 - 2.5 * yn1 + 2 * yp1 - 0.5 * yp2;
		double C = -0.5 * yn2 + 0.5 * yp1;
		double D = yn1;
		return A * w * w * w + B * w * w + C * w + D;
	}

	/**
	 * Interpolates within the center square of a 4-by-4 grid of samples.
	 *
	 * @param x x coordinate between the two middle columns
	 * @param y y coordinate between the two middle rows
	 * @param local16 surrounding samples, indexed by x and y
	 * @return the bicubic interpolated value at (x, y)
	 */
	public static double interpolate2d(double x, double y, double[][] local16) {
		double[] section = new double[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate1d(y, local16[i][0], local16[i][1], local16[i][2], local16[i][3]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Interpolates within the center cube of a 4-by-4-by-4 grid of samples.
	 *
	 * @param x x coordinate between the two middle sample planes
	 * @param y y coordinate between the two middle sample planes
	 * @param z z coordinate between the two middle sample planes
	 * @param local64 surrounding samples, indexed by x, y and z
	 * @return the tricubic interpolated value at (x, y, z)
	 */
	public static double interpolate3d(double x, double y, double z, double[][][] local64) {
		double[] section = new double[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate2d(y, z, local64[i]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Interpolates between the two middle samples of a four-point cubic curve.
	 *
	 * @param x x coordinate to interpolate
	 * @param yn2 sample at f(floor(x) - 1)
	 * @param yn1 sample at f(floor(x))
	 * @param yp1 sample at f(floor(x) + 1)
	 * @param yp2 sample at f(floor(x) + 2)
	 * @return the interpolated value
	 */
	public static float interpolate(double x, float yn2, float yn1, float yp1, float yp2) {
		return interpolate1d(x, yn2, yn1, yp1, yp2);
	}

	/**
	 * Interpolates between the two middle samples of a four-point cubic curve.
	 *
	 * @param x x coordinate to interpolate
	 * @param yn2 sample at f(floor(x) - 1)
	 * @param yn1 sample at f(floor(x))
	 * @param yp1 sample at f(floor(x) + 1)
	 * @param yp2 sample at f(floor(x) + 2)
	 * @return the interpolated value
	 */
	public static float interpolate1d(double x, float yn2, float yn1, float yp1, float yp2) {
		float w = (float) (x - floor(x));
		if (w == 0 && x != 0)
			return yn1; // Snap near the endpoints to avoid floating-point artifacts.
		if (w < 0.0001) {
			return yn1;
		}
		if (w > 0.999) {
			return yp1;
		}
		// Cubic coefficients adapted from http://www.paulinternet.nl/?page=bicubic
		float A = -0.5f * yn2 + 1.5f * yn1 - 1.5f * yp1 + 0.5f * yp2;
		float B = yn2 - 2.5f * yn1 + 2f * yp1 - 0.5f * yp2;
		float C = -0.5f * yn2 + 0.5f * yp1;
		float D = yn1;
		return A * w * w * w + B * w * w + C * w + D;
	}

	/**
	 * Interpolates within the center square of a 4-by-4 grid of samples.
	 *
	 * @param x x coordinate between the two middle columns
	 * @param y y coordinate between the two middle rows
	 * @param local16 surrounding samples, indexed by x and y
	 * @return the bicubic interpolated value at (x, y)
	 */
	public static float interpolate2d(double x, double y, float[][] local16) {
		float[] section = new float[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate1d(y, local16[i][0], local16[i][1], local16[i][2], local16[i][3]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Interpolates within the center cube of a 4-by-4-by-4 grid of samples.
	 *
	 * @param x x coordinate between the two middle sample planes
	 * @param y y coordinate between the two middle sample planes
	 * @param z z coordinate between the two middle sample planes
	 * @param local64 surrounding samples, indexed by x, y and z
	 * @return the tricubic interpolated value at (x, y, z)
	 */
	public static float interpolate3d(double x, double y, double z, float[][][] local64) {
		float[] section = new float[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate2d(y, z, local64[i]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	public static int floor(double x) {
		if (x >= 0.0) {
			return (int) x;
		} else {
			return ((int) x) - 1;
		}
	}
}
