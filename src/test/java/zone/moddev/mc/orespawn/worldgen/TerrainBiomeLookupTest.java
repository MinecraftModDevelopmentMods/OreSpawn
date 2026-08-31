package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TerrainBiomeLookupTest {
	@Test
	void geologyAndSamplerHeightsResolveThroughTheSameQuartBiome() {
		AtomicReference<String> coordinates = new AtomicReference<>();
		assertNull(TerrainBiomeLookup.atBlock((x, y, z) -> {
			coordinates.set(x + "," + y + "," + z);
			return null;
		}, 13, 62, -32));
		assertEquals("3,15,-8", coordinates.get());

		assertNull(TerrainBiomeLookup.atBlock((x, y, z) -> {
			coordinates.set(x + "," + y + "," + z);
			return null;
		}, 13, 63, -32));
		assertEquals("3,15,-8", coordinates.get(),
				"later surface work must not move an adjacent height into a fuzzy biome cell");
	}
}
