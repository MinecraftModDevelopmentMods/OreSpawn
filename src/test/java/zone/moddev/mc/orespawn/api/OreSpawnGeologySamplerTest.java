package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OreSpawnGeologySamplerTest {
	@Test
	void convertsLevelHeightToTheGenerationBiomeHeight() {
		assertEquals(96, OreSpawnGeologySampler.generationBiomeY(97, -64));
		assertEquals(-1, OreSpawnGeologySampler.generationBiomeY(0, -64));
	}

	@Test
	void clampsAnEmptyColumnToTheLevelFloor() {
		assertEquals(-64, OreSpawnGeologySampler.generationBiomeY(-64, -64));
		assertEquals(-64, OreSpawnGeologySampler.generationBiomeY(Integer.MIN_VALUE, -64));
	}
}
