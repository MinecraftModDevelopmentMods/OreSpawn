package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class OreHeightDistributionTest {
	@Test
	void bottomBiasedSamplersRemainBoundedAndMonotonicallyDeeper() {
		double uniform = mean(OreHeightDistribution.UNIFORM);
		double triangle = mean(OreHeightDistribution.TRIANGLE);
		double mixed = mean(OreHeightDistribution.UNIFORM_BOTTOM_TRIANGLE);
		double bottom = mean(OreHeightDistribution.BOTTOM_TRIANGLE);

		assertTrue(uniform > 49.0D && uniform < 51.0D);
		assertTrue(triangle > 49.0D && triangle < 51.0D);
		assertTrue(mixed > 32.0D && mixed < 34.5D);
		assertTrue(Math.abs(mixed - bottom) < 1.0D);
		assertTrue(bottom > 32.0D && bottom < 34.5D);
	}

	private static double mean(OreHeightDistribution distribution) {
		Random random = new Random(19780401L);
		long total = 0L;
		int samples = 100_000;
		for (int i = 0; i < samples; i++) {
			int value = distribution.sample(random, 0, 100);
			assertTrue(value >= 0 && value <= 100);
			total += value;
		}
		return total / (double) samples;
	}
}
