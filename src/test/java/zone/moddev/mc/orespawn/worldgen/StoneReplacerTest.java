package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class StoneReplacerTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge14TestBootstrap.registerVanilla();
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
}
