package zone.moddev.mc.orespawn.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import zone.moddev.mc.orespawn.api.OrePlacementContext;

import net.minecraft.fluid.Fluid;

import org.junit.jupiter.api.Test;

class ConnectedOreShapeTest {
	@Test
	void everyOrientationAndPrefixIsFaceConnected() {
		for (int orientation = 0; orientation < ConnectedOreShape.ORIENTATIONS; orientation++) {
			Set<String> placed = new HashSet<>();
			for (int candidate = 0; candidate < ConnectedOreShape.MAX_SIZE; candidate++) {
				int x = ConnectedOreShape.x(orientation, candidate);
				int y = ConnectedOreShape.y(orientation, candidate);
				int z = ConnectedOreShape.z(orientation, candidate);
				if (candidate > 0) {
					assertTrue(hasNeighbour(placed, x, y, z),
							"Disconnected candidate " + candidate + " in orientation " + orientation);
				}
				assertTrue(placed.add(key(x, y, z)), "Duplicate connected-shape offset");
			}
		}
	}

	@Test
	void placementReachesRequestedQuantityWithoutDuplicateAttempts() {
		RecordingContext context = new RecordingContext(17L);
		assertTrue(ConnectedOreShape.place(context, 40, 20, -12, 64));
		assertEquals(64, context.positions.size());
		assertEquals(64, context.attempts);
	}

	private static boolean hasNeighbour(Set<String> placed, int x, int y, int z) {
		return placed.contains(key(x + 1, y, z)) || placed.contains(key(x - 1, y, z))
				|| placed.contains(key(x, y + 1, z)) || placed.contains(key(x, y - 1, z))
				|| placed.contains(key(x, y, z + 1)) || placed.contains(key(x, y, z - 1));
	}

	private static String key(int x, int y, int z) {
		return x + "," + y + "," + z;
	}

	private static final class RecordingContext implements OrePlacementContext {
		private final Random random;
		private final Set<String> positions = new HashSet<>();
		private int attempts;

		RecordingContext(long seed) {
			random = new Random(seed);
		}

		@Override public Random random() { return random; }
		@Override public int originX() { return 0; }
		@Override public int originY() { return 0; }
		@Override public int originZ() { return 0; }
		@Override public int minY() { return -64; }
		@Override public int maxY() { return 319; }
		@Override public int quantity() { return 64; }
		@Override public int spread() { return 8; }
		@Override public int verticalSpread() { return 4; }
		@Override public int nodeSize() { return 4; }
		@Override public boolean inside(int x, int y, int z) { return true; }
		@Override public boolean isFluid(int x, int y, int z, Fluid fluid) { return false; }

		@Override
		public boolean tryPlace(int x, int y, int z) {
			attempts++;
			return positions.add(key(x, y, z));
		}
	}
}
