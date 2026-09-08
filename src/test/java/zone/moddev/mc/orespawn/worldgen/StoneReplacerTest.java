package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge13TestBootstrap;

class StoneReplacerTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge13TestBootstrap.registerVanilla();
	}

	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				0, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				0, true, true));
		assertFalse(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.STONE.getDefaultState()));
		assertTrue(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.STONE.getStateFromMeta(1)));
	}

	@Test
	void nonOverworldBiomesAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				-1, true, true));
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				1, true, true));
	}

	@Test
	void invalidTerrainHostsRemainUnsafeEvenWhenDeclared() {
		LinkedHashSet<net.minecraft.block.Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.CHEST);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				new net.minecraft.util.ResourceLocation("surfaceprobe:the_end"),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.getDefaultState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.getDefaultState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.getDefaultState()));
		assertFalse(terrain.isReplaceable(Blocks.CHEST.getDefaultState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.getDefaultState()));
	}
}
