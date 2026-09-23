package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;

class OreGenerationContextTest {
	@Test
	void extendsTheOriginalPlacementContractWithoutChangingItsAbi() {
		assertTrue(OrePlacementContext.class.isAssignableFrom(OreGenerationContext.class));
		Set<String> baseMethods = Arrays.stream(OrePlacementContext.class.getDeclaredMethods())
				.map(Method::getName).collect(Collectors.toSet());
		assertFalse(baseMethods.contains("worldSeed"));
		assertFalse(baseMethods.contains("dimension"));
		assertFalse(baseMethods.contains("chunkX"));
		assertFalse(baseMethods.contains("chunkZ"));
		assertFalse(baseMethods.contains("geologySampler"));
	}

	@Test
	void exposesTheStableRegionDepositIdentityContract() throws Exception {
		assertEquals(long.class, OreGenerationContext.class.getMethod("worldSeed").getReturnType());
		assertEquals(ResourceLocation.class,
				OreGenerationContext.class.getMethod("dimension").getReturnType());
		assertEquals(int.class, OreGenerationContext.class.getMethod("chunkX").getReturnType());
		assertEquals(int.class, OreGenerationContext.class.getMethod("chunkZ").getReturnType());
		assertEquals(Optional.class,
				OreGenerationContext.class.getMethod("geologySampler").getReturnType());
	}

	@Test
	void oreSpawnPatternContextImplementsTheExtendedContract() throws Exception {
		Class<?> implementation = Class.forName(
				"zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration$PatternContext");
		assertTrue(OreGenerationContext.class.isAssignableFrom(implementation));
	}
}
