package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import net.minecraft.world.level.material.Fluids;

class VanillaSpringCompatibilityTest {
	@Test
	void configuredRocksExtendVanillaSpringHostsWithoutDuplicates() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());

		HolderSet<Block> expanded = VanillaSpringCompatibility.merge(original,
				Arrays.asList(Blocks.STONE, Blocks.DIAMOND_BLOCK));

		assertEquals(3, expanded.size());
		assertTrue(expanded.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(expanded.contains(Blocks.DIRT.builtInRegistryHolder()));
		assertTrue(expanded.contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));
	}

	@Test
	void emptyTerrainProfileRestoresVanillaSpringHosts() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());

		HolderSet<Block> restored =
				VanillaSpringCompatibility.merge(original, Collections.emptyList());

		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(restored.contains(Blocks.DIRT.builtInRegistryHolder()));
	}

	@Test
	void repeatedUpdatesRetainTheOriginalSpringHosts() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());
		SpringConfiguration spring = new SpringConfiguration(
				Fluids.LAVA.defaultFluidState(), true, 4, 1, original);

		VanillaSpringCompatibility.update(spring,
				Collections.singleton(Blocks.DIAMOND_BLOCK));
		assertTrue(spring.validBlocks.contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));

		VanillaSpringCompatibility.update(spring, Collections.emptyList());
		assertEquals(original, spring.validBlocks);
	}
}
