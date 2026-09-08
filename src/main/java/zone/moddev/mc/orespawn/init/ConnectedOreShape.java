package zone.moddev.mc.orespawn.init;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import zone.moddev.mc.orespawn.api.OrePlacementContext;

/** Pre-baked connected node shapes shared by compact, cluster, and vein patterns. */
final class ConnectedOreShape {
	static final int MAX_SIZE = 64;
	static final int ORIENTATIONS = 48;
	private static final byte[] X = new byte[MAX_SIZE * ORIENTATIONS];
	private static final byte[] Y = new byte[MAX_SIZE * ORIENTATIONS];
	private static final byte[] Z = new byte[MAX_SIZE * ORIENTATIONS];

	private static final byte[] SEED_X = {
			0, 1, -1, 0, 0, 0, 0,
			1, 1, -1, -1, 1, 1, -1, -1, 0, 0, 0, 0,
			1, 1, 1, 1, -1, -1, -1, -1,
			2, -2, 0, 0, 0
	};
	private static final byte[] SEED_Y = {
			0, 0, 0, 1, -1, 0, 0,
			1, -1, 1, -1, 0, 0, 0, 0, 1, 1, -1, -1,
			1, 1, -1, -1, 1, 1, -1, -1,
			0, 0, 2, -2, 0
	};
	private static final byte[] SEED_Z = {
			0, 0, 0, 0, 0, 1, -1,
			0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1,
			1, -1, 1, -1, 1, -1, 1, -1,
			0, 0, 0, 0, 2
	};

	static {
		byte[] baseX = new byte[MAX_SIZE];
		byte[] baseY = new byte[MAX_SIZE];
		byte[] baseZ = new byte[MAX_SIZE];
		System.arraycopy(SEED_X, 0, baseX, 0, SEED_X.length);
		System.arraycopy(SEED_Y, 0, baseY, 0, SEED_Y.length);
		System.arraycopy(SEED_Z, 0, baseZ, 0, SEED_Z.length);

		List<int[]> candidates = new ArrayList<>();
		for (int x = -4; x <= 4; x++) {
			for (int y = -4; y <= 4; y++) {
				for (int z = -4; z <= 4; z++) {
					if (!containsSeed(x, y, z)) candidates.add(new int[] { x, y, z });
				}
			}
		}
		candidates.sort(Comparator
				.comparingInt(ConnectedOreShape::distanceSquared)
				.thenComparingInt(ConnectedOreShape::manhattan)
				.thenComparingInt(value -> value[0])
				.thenComparingInt(value -> value[1])
				.thenComparingInt(value -> value[2]));
		for (int i = SEED_X.length; i < MAX_SIZE; i++) {
			int[] candidate = candidates.get(i - SEED_X.length);
			baseX[i] = (byte) candidate[0];
			baseY[i] = (byte) candidate[1];
			baseZ[i] = (byte) candidate[2];
		}

		for (int orientation = 0; orientation < ORIENTATIONS; orientation++) {
			int permutation = orientation % 6;
			int signs = orientation / 6;
			for (int i = 0; i < MAX_SIZE; i++) {
				int a = baseX[i];
				int b = baseY[i];
				int c = baseZ[i];
				int x;
				int y;
				int z;
				switch (permutation) {
					case 1: x = a; y = c; z = b; break;
					case 2: x = b; y = a; z = c; break;
					case 3: x = b; y = c; z = a; break;
					case 4: x = c; y = a; z = b; break;
					case 5: x = c; y = b; z = a; break;
					default: x = a; y = b; z = c; break;
				}
				int index = (orientation * MAX_SIZE) + i;
				X[index] = (byte) ((signs & 1) == 0 ? x : -x);
				Y[index] = (byte) ((signs & 2) == 0 ? y : -y);
				Z[index] = (byte) ((signs & 4) == 0 ? z : -z);
			}
		}
	}

	private ConnectedOreShape() {
	}

	static boolean place(OrePlacementContext context, int centerX, int centerY, int centerZ,
			int targetSize) {
		int target = Math.max(1, Math.min(MAX_SIZE, targetSize));
		int placed = 0;
		int offsetBase = context.random().nextInt(ORIENTATIONS) * MAX_SIZE;
		for (int candidate = 0; candidate < MAX_SIZE && placed < target; candidate++) {
			int offset = offsetBase + candidate;
			if (context.tryPlace(centerX + X[offset], centerY + Y[offset], centerZ + Z[offset])) {
				placed++;
			}
		}
		return placed > 0;
	}

	static int x(int orientation, int candidate) {
		return X[(orientation * MAX_SIZE) + candidate];
	}

	static int y(int orientation, int candidate) {
		return Y[(orientation * MAX_SIZE) + candidate];
	}

	static int z(int orientation, int candidate) {
		return Z[(orientation * MAX_SIZE) + candidate];
	}

	private static boolean containsSeed(int x, int y, int z) {
		for (int i = 0; i < SEED_X.length; i++) {
			if (SEED_X[i] == x && SEED_Y[i] == y && SEED_Z[i] == z) return true;
		}
		return false;
	}

	private static int distanceSquared(int[] value) {
		return (value[0] * value[0]) + (value[1] * value[1]) + (value[2] * value[2]);
	}

	private static int manhattan(int[] value) {
		return Math.abs(value[0]) + Math.abs(value[1]) + Math.abs(value[2]);
	}
}
