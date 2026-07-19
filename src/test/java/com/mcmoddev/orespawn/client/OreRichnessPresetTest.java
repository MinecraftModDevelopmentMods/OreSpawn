package com.mcmoddev.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OreRichnessPresetTest {
	@Test
	void scalesExpectedAbundanceAroundTheOreBaseline() {
		assertEquals(3.0D, OreRichnessPreset.ULTRA_POOR.scaledFrequency(12.0D));
		assertEquals(6.0D, OreRichnessPreset.POOR.scaledFrequency(12.0D));
		assertEquals(12.0D, OreRichnessPreset.AVERAGE.scaledFrequency(12.0D));
		assertEquals(24.0D, OreRichnessPreset.RICH.scaledFrequency(12.0D));
		assertEquals(48.0D, OreRichnessPreset.ULTRA_RICH.scaledFrequency(12.0D));
	}

	@Test
	void respectsTheWorldgenFrequencySafetyLimit() {
		assertEquals(64.0D, OreRichnessPreset.RICH.scaledFrequency(34.0D));
		assertEquals(64.0D, OreRichnessPreset.ULTRA_RICH.scaledFrequency(34.0D));
	}

	@Test
	void recognizesAPreviouslySelectedPreset() {
		assertEquals(OreRichnessPreset.POOR, OreRichnessPreset.fromFrequency(12.0D, 6.0D));
		assertEquals(OreRichnessPreset.AVERAGE, OreRichnessPreset.fromFrequency(12.0D, 7.0D));
	}
}
