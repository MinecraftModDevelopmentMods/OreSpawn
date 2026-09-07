package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OreSpawnGeologySamplerTest {
	@Test
	void convertsLevelHeightToTheGenerationBiomeHeight() {
		assertEquals(96, OreSpawnGeologySampler.generationBiomeY(97, 0));
		assertEquals(0, OreSpawnGeologySampler.generationBiomeY(1, 0));
	}

	@Test
	void clampsAnEmptyColumnToTheLevelFloor() {
		assertEquals(0, OreSpawnGeologySampler.generationBiomeY(0, 0));
		assertEquals(0, OreSpawnGeologySampler.generationBiomeY(Integer.MIN_VALUE, 0));
	}
}
