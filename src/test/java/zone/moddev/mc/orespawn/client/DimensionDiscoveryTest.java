package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class DimensionDiscoveryTest {
	@Test
	void convertsNestedDimensionDataPathsToRegistryIds() {
		Set<String> dimensions = new TreeSet<>();
		DimensionDiscovery.addDimensionId(dimensions, "examplemod", "moon/caverns");
		DimensionDiscovery.addDimensionId(dimensions, "Invalid Namespace", "ignored");

		assertEquals(Collections.singleton("examplemod:moon/caverns"), dimensions);
	}
}
