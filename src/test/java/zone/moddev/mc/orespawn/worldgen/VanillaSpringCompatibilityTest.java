package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.world.gen.feature.Features;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.feature.LiquidsConfig;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Bootstrap.bootStrap();
	}

	@Test
	void configuredRocksExtendVanillaSpringHostsWithoutDuplicates() {
		java.util.Set<Block> original = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		original.add(Blocks.STONE);
		original.add(Blocks.DIRT);

		java.util.Set<Block> expanded = VanillaSpringCompatibility.merge(original,
				Arrays.asList(Blocks.STONE, Blocks.DIAMOND_BLOCK));

		assertTrue(expanded instanceof ImmutableSet,
				"Minecraft 1.16.5's spring codec requires an ImmutableSet");
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
				"Minecraft 1.16.5's spring codec requires an ImmutableSet");
		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE));
		assertTrue(restored.contains(Blocks.DIRT));
	}

	@Test
	void refreshUpdatesOrdinaryOverworldSpringsAndRestoresThem() {
		LiquidsConfig lava = VanillaSpringCompatibility.vanillaSpring(Features.SPRING_LAVA);
		LiquidsConfig water = VanillaSpringCompatibility.vanillaSpring(Features.SPRING_WATER);

		try {
			VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));

			assertTrue(lava.validBlocks instanceof ImmutableSet);
			assertTrue(water.validBlocks instanceof ImmutableSet);
			assertTrue(LiquidsConfig.CODEC.encodeStart(JsonOps.INSTANCE, lava).result().isPresent(),
					"The modified spring must remain serializable by the 1.16.5 registry codec");
			assertTrue(lava.validBlocks.contains(Blocks.DIAMOND_BLOCK));
			assertTrue(water.validBlocks.contains(Blocks.DIAMOND_BLOCK));
		} finally {
			VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
		}

		assertTrue(lava.validBlocks.contains(Blocks.STONE));
		assertTrue(water.validBlocks.contains(Blocks.STONE));
	}
}
