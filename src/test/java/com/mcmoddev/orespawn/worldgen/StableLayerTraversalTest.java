package com.mcmoddev.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StableLayerTraversalTest {
	@Test
	void incrementalColumnWalkMatchesPerBlockLayerCalculation() {
		int[] thicknesses = { 1, 3, 8, 28, 128 };
		int[] offsets = { -511, -129, -64, -1, 0, 1, 63, 128, 511 };
		for (int thickness : thicknesses) {
			for (int offset : offsets) {
				int layer = Math.floorDiv(offset + 319, thickness);
				int layerStart = layer * thickness;
				for (int y = 319; y >= -64; y--) {
					int stratum = offset + y;
					if (stratum < layerStart) {
						layer--;
						layerStart -= thickness;
					}
					assertEquals(Math.floorDiv(stratum, thickness), layer,
							"Layer drifted for thickness " + thickness + ", offset " + offset + ", y " + y);
				}
			}
		}
	}
}
