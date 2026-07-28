package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.minecraft.core.HolderSet;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

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

		HolderSet<Block> restored = VanillaSpringCompatibility.merge(original, Collections.emptyList());

		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(restored.contains(Blocks.DIRT.builtInRegistryHolder()));
	}

	@Test
	void refreshUpdatesOrdinaryOverworldSpringsAndRestoresThem() {
		SpringConfiguration lava = MiscOverworldFeatures.SPRING_LAVA_OVERWORLD.value().config();
		SpringConfiguration water = MiscOverworldFeatures.SPRING_WATER.value().config();
		SpringConfiguration frozen = MiscOverworldFeatures.SPRING_LAVA_FROZEN.value().config();
		HolderSet<Block> frozenHosts = frozen.validBlocks;

		try {
			VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));

			assertTrue(lava.validBlocks.contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));
			assertTrue(water.validBlocks.contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));
			assertEquals(frozenHosts, frozen.validBlocks);
		} finally {
			VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
		}

		assertTrue(lava.validBlocks.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(water.validBlocks.contains(Blocks.STONE.builtInRegistryHolder()));
	}
}
