package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;

import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.GeomeDefinition;
import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.RockEntry;

class StableLayerHeightEligibilityTest {
	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void rockBoundsUseActualWorldYWhileFormationIdentityRemainsShifted() {
		BakedGeomeConfig config = netherFloorConfig();
		GeomeGeology geology = new GeomeGeology(0L, config);
		double[] geomeScores = { 1.0D };

		assertEquals(Blocks.BASALT,
				geology.getStoneAt(0, geomeScores, -64, 0L, 0, 1, 0),
				"a legal Nether Y must not fall back to Stone when waviness shifts its formation below min_y");
		assertEquals(Blocks.STONE,
				geology.getStoneAt(0, geomeScores, 64, 0L, 0, -1, 0),
				"a shifted formation inside the range must not make an illegal actual Y eligible");
	}

	private static BakedGeomeConfig netherFloorConfig() {
		GeomeDefinition[] geomes = {
				new GeomeDefinition("test:nether", 1.0D, new double[] { 0.0D, 0.0D, 0.0D, 1.0D })
		};
		RockEntry[] rocks = {
				new RockEntry(Blocks.BASALT.defaultBlockState(), RockFamily.IGNEOUS_VOLCANIC,
						24, 68, 0, 127, 1.0D, true, new double[] { 1.0D })
		};
		FormationSettings formations = new FormationSettings(FormationSettings.Algorithm.STABLE_LAYERS,
				32.0D, 8192.0D, 8, 512.0D, 96.0D, 24.0D, 3, 0.85D);
		return new BakedGeomeConfig(geomes, 384.0D, 1.15D, 0.9D, 0.45D,
				Collections.emptyMap(), Collections.emptyMap(), rocks, formations);
	}
}
