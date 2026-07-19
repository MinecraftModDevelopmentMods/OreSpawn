package com.mcmoddev.orespawn.worldgen.math;

/**
 * This is a convenience class that provides multidimensional cubic
 * interpolation methods. This class is abstract and not meant to be extended
 *
 * @author Cyanobacterium
 */
public abstract class CubicInterpolator {

	/**
	 * Interpolate with cubic approximation for a point X on a grid. X
	 * must lie between the X values of the yn1 and yp1 control points.
	 *
	 * @param x
	 *            x coordinate to interpolate
	 * @param yn2
	 *            Y value at f(floor(x)-1)
	 * @param yn1
	 *            Y value at f(floor(x)-0)
	 * @param yp1
	 *            Y value at f(floor(x)+1)
	 * @param yp2
	 *            Y value at f(floor(x)+2)
	 * @return A cubic-interpolated value from the given control points.
	 */
	public static double interpolate(double x, double yn2, double yn1, double yp1, double yp2) {
		return interpolate1d(x, yn2, yn1, yp1, yp2);
	}

	/**
	 * Interpolate with cubic approximation for a point X on a grid. X
	 * must lie between the X values of the yn1 and yp1 control points.
	 *
	 * @param x
	 *            x coordinate to interpolate
	 * @param yn2
	 *            Y value at f(floor(x)-1)
	 * @param yn1
	 *            Y value at f(floor(x)-0)
	 * @param yp1
	 *            Y value at f(floor(x)+1)
	 * @param yp2
	 *            Y value at f(floor(x)+2)
	 * @return A cubic-interpolated value from the given control points.
	 */
	public static double interpolate1d(double x, double yn2, double yn1, double yp1, double yp2) {
		double w = x - Math.floor(x);
		if (w == 0 && x != 0)
			return yp1; // w should be 1, but the way this is calcluated it doesn't work right
		// prevent precision-loss artifacts
		if (w < 0.000976563) {
			return yn1;
		}
		if (w > 0.999023438) {
			return yp1;
		}
		// adapted from http://www.paulinternet.nl/?page=bicubic
		double A = -0.5 * yn2 + 1.5 * yn1 - 1.5 * yp1 + 0.5 * yp2;
		double B = yn2 - 2.5 * yn1 + 2 * yp1 - 0.5 * yp2;
		double C = -0.5 * yn2 + 0.5 * yp1;
		double D = yn1;
		return A * w * w * w + B * w * w + C * w + D;
	}

	/**
	 * Returns the bi-cubic interpolation of the (x,y) coordinate inide
	 * the provided grid of control points. (x,y) is assumed to be in the
	 * center square of the unit grid.
	 *
	 * @param x
	 *            x coordinate between local16[1][y] and local16[2][y]
	 * @param y
	 *            y coordinate between local16[x][1] and local16[x][2]
	 * @param local16
	 *            Array [x][y] of the 4x4 grid around the coordinate
	 * @return Returns the bi-cubic interpolation of the (x,y) coordinate.
	 */
	public static double interpolate2d(double x, double y, double[][] local16) {
		double[] section = new double[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate1d(y, local16[i][0], local16[i][1], local16[i][2], local16[i][3]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Performs a tri-cubic interpolation of the (x,y,z) coordinate near
	 * the center of the provided unit grid of surrounding control points.
	 *
	 * @param x
	 *            x coordinate in the middle of the array space
	 * @param y
	 *            y coordinate in the middle of the array space
	 * @param z
	 *            z coordinate in the middle of the array space
	 * @param local64
	 *            Array [x][y][z] of the 4x4x4 grid around the coordinate
	 * @return Returns the tri-cubic interpolation of the given coordinate.
	 */
	public static double interpolate3d(double x, double y, double z, double[][][] local64) {
		double[] section = new double[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate2d(y, z, local64[i]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Interpolate with cubic approximation for a point X on a grid. X
	 * must lie between the X values of the yn1 and yp1 control points.
	 *
	 * @param x
	 *            x coordinate to interpolate
	 * @param yn2
	 *            Y value at f(floor(x)-1)
	 * @param yn1
	 *            Y value at f(floor(x)-0)
	 * @param yp1
	 *            Y value at f(floor(x)+1)
	 * @param yp2
	 *            Y value at f(floor(x)+2)
	 * @return A cubic-interpolated value from the given control points.
	 */
	public static float interpolate(double x, float yn2, float yn1, float yp1, float yp2) {
		return interpolate1d(x, yn2, yn1, yp1, yp2);
	}

	/**
	 * Interpolate with cubic approximation for a point X on a grid. X
	 * must lie between the X values of the yn1 and yp1 control points.
	 *
	 * @param x
	 *            x coordinate to interpolate
	 * @param yn2
	 *            Y value at f(floor(x)-1)
	 * @param yn1
	 *            Y value at f(floor(x)-0)
	 * @param yp1
	 *            Y value at f(floor(x)+1)
	 * @param yp2
	 *            Y value at f(floor(x)+2)
	 * @return A cubic-interpolated value from the given control points.
	 */
	public static float interpolate1d(double x, float yn2, float yn1, float yp1, float yp2) {
		float w = (float) (x - floor(x));
		if (w == 0 && x != 0)
			return yn1; // w should be 1, but the way this is calcluated it doesn't work right
		// prevent precision-loss artifacts
		if (w < 0.0001) {
			return yn1;
		}
		if (w > 0.999) {
			return yp1;
		}
		// adapted from http://www.paulinternet.nl/?page=bicubic
		float A = -0.5f * yn2 + 1.5f * yn1 - 1.5f * yp1 + 0.5f * yp2;
		float B = yn2 - 2.5f * yn1 + 2f * yp1 - 0.5f * yp2;
		float C = -0.5f * yn2 + 0.5f * yp1;
		float D = yn1;
		return A * w * w * w + B * w * w + C * w + D;
	}

	/**
	 * Returns the bi-cubic interpolation of the (x,y) coordinate inside
	 * the provided grid of control points. (x,y) is assumed to be in the
	 * center square of the unit grid.
	 *
	 * @param x
	 *            x coordinate between local16[1][y] and local16[2][y]
	 * @param y
	 *            y coordinate between local16[x][1] and local16[x][2]
	 * @param local16
	 *            Array [x][y] of the 4x4 grid around the coordinate
	 * @return Returns the bi-cubic interpolation of the (x,y) coordinate.
	 */
	public static float interpolate2d(double x, double y, float[][] local16) {
		float[] section = new float[4];
		for (int i = 0; i < 4; i++) {
			section[i] = interpolate1d(y, local16[i][0], local16[i][1], local16[i][2], local16[i][3]);
		}
		return interpolate1d(x, section[0], section[1], section[2], section[3]);
	}

	/**
	 * Performs a tri-cubic interpolation of the (x,y,z) coordinate near
	 * the center of the provided unit grid of surrounding control points.
	 *
	 * @param x
	 *            x coordinate in the middle of the array space
	 * @param y
	 *            y coordinate in the middle of the array space
	 * @param z
	 *            z coordinate in the middle of the array space
	 * @param local64
	 *            Array [x][y][z] of the 4x4x4 grid around the coordinate
	 * @return Returns the tri-cubic interpolation of the given coordinate.
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
