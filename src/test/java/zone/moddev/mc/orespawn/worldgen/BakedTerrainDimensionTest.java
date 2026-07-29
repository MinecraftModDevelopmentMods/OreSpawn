package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BakedTerrainDimensionTest {
	@Test
	void customDimensionAcceptsOnlyConfiguredBiomeNamespaces() {
		BakedTerrainDimension dimension = new BakedTerrainDimension(null, Collections.emptySet(),
				Collections.singleton("examplemod"), Collections.emptySet());

		assertTrue(dimension.acceptsBiome(ResourceLocation.parse("examplemod:crystal_fields")));
		assertFalse(dimension.acceptsBiome(ResourceLocation.parse("minecraft:plains")));
	}
}
