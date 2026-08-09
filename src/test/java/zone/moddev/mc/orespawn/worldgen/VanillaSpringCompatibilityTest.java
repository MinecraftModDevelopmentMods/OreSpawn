package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.google.common.collect.ImmutableSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.biome.DefaultBiomeFeatures;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.feature.LiquidsConfig;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Bootstrap.register();
	}

	@Test
	void configuredRocksExtendVanillaSpringHostsWithoutDuplicates() {
		java.util.Set<Block> original = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		original.add(Blocks.STONE);
		original.add(Blocks.DIRT);

		java.util.Set<Block> expanded = VanillaSpringCompatibility.merge(original,
				Arrays.asList(Blocks.STONE, Blocks.DIAMOND_BLOCK));

		assertTrue(expanded instanceof ImmutableSet,
				"Minecraft 1.15.2 spring hosts remain immutable");
		assertEquals(3, expanded.size());
		assertTrue(expanded.contains(Blocks.STONE));
		assertTrue(expanded.contains(Blocks.DIRT));
		assertTrue(expanded.contains(Blocks.DIAMOND_BLOCK));
	}

	@Test
	void emptyTerrainProfileRestoresVanillaSpringHosts() {
		java.util.Set<Block> original = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		original.add(Blocks.STONE);
		original.add(Blocks.DIRT);

		java.util.Set<Block> restored = VanillaSpringCompatibility.merge(original, Collections.emptyList());

		assertTrue(restored instanceof ImmutableSet,
				"Minecraft 1.15.2 spring hosts remain immutable");
		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE));
		assertTrue(restored.contains(Blocks.DIRT));
	}

	@Test
	void refreshUpdatesOrdinaryOverworldSpringsAndRestoresThem() {
		LiquidsConfig lava = DefaultBiomeFeatures.LAVA_SPRING_CONFIG;
		LiquidsConfig water = DefaultBiomeFeatures.WATER_SPRING_CONFIG;

		try {
			VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));

			assertTrue(lava.acceptedBlocks instanceof ImmutableSet);
			assertTrue(water.acceptedBlocks instanceof ImmutableSet);
			assertTrue(lava.acceptedBlocks.contains(Blocks.DIAMOND_BLOCK));
			assertTrue(water.acceptedBlocks.contains(Blocks.DIAMOND_BLOCK));
		} finally {
			VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
		}

		assertTrue(lava.acceptedBlocks.contains(Blocks.STONE));
		assertTrue(water.acceptedBlocks.contains(Blocks.STONE));
	}
}
