package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.data.worldgen.Features;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

class VanillaSpringCompatibilityTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
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
				"Minecraft 1.17.1's spring codec requires an ImmutableSet");
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
				"Minecraft 1.17.1's spring codec requires an ImmutableSet");
		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE));
		assertTrue(restored.contains(Blocks.DIRT));
	}

	@Test
	void refreshUpdatesOrdinaryOverworldSpringsAndRestoresThem() {
		SpringConfiguration lava = VanillaSpringCompatibility.vanillaSpring(Features.SPRING_LAVA);
		SpringConfiguration water = VanillaSpringCompatibility.vanillaSpring(Features.SPRING_WATER);

		try {
			VanillaSpringCompatibility.refreshBlocks(Collections.singleton(Blocks.DIAMOND_BLOCK));

			assertTrue(lava.validBlocks instanceof ImmutableSet);
			assertTrue(water.validBlocks instanceof ImmutableSet);
			assertTrue(SpringConfiguration.CODEC.encodeStart(JsonOps.INSTANCE, lava).result().isPresent(),
					"The modified spring must remain serializable by the 1.17.1 registry codec");
			assertTrue(lava.validBlocks.contains(Blocks.DIAMOND_BLOCK));
			assertTrue(water.validBlocks.contains(Blocks.DIAMOND_BLOCK));
		} finally {
			VanillaSpringCompatibility.refreshBlocks(Collections.emptyList());
		}

		assertTrue(lava.validBlocks.contains(Blocks.STONE));
		assertTrue(water.validBlocks.contains(Blocks.STONE));
	}
}
