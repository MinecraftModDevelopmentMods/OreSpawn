package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.block.Blocks;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome.Category;

class StoneReplacerTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.register();
	}

	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.PLAINS, true, true));
		assertFalse(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.STONE.getDefaultState()));
		assertTrue(GeomeGeology.changes(Blocks.STONE.getDefaultState(),
				Blocks.GRANITE.getDefaultState()));
	}

	@Test
	void nonOverworldBiomesAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.NETHER, true, true));
		assertFalse(TerrainFeaturePolicy.shouldRemoveVanillaMatchingStoneFeatures(
				Category.THEEND, true, true));
	}
}
