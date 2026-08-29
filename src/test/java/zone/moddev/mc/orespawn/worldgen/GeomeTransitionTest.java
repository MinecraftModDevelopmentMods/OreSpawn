package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.GeomeDefinition;
import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.RockEntry;

class GeomeTransitionTest {
	private static final ResourceLocation WINDSWEPT_HILLS = new ResourceLocation("minecraft:windswept_hills");

	@Test
	void configuredBiomeWeightsWorkWithoutAForgeBiomeRegistryEntry() {
		Map<String, Integer> indexes = new LinkedHashMap<>();
		indexes.put("orespawn:first", 0);
		indexes.put("orespawn:mountain_belt", 1);
		Map<ResourceLocation, double[]> weights = GeomeConfig.bakeBiomeIdentifierWeights(indexes,
				Map.of(WINDSWEPT_HILLS.toString(), new double[] { 1.0D, 4.0D }));
		BakedGeomeConfig config = config(weights);

		assertEquals(1, config.pickGeome(null, WINDSWEPT_HILLS, new double[2], 0.0D));
	}

	@Test
	void identifierFallbackRetainsDictionaryWeightContributions() {
		Map<String, Integer> indexes = new LinkedHashMap<>();
		indexes.put("cakeworld:peppermint_fold", 0);
		indexes.put("cakeworld:rock_candy_uplift", 1);
		ResourceLocation marshmallowPeaks = new ResourceLocation("cakeworld", "marshmallow_peaks");
		Map<ResourceLocation, double[]> weights = GeomeConfig.bakeBiomeIdentifierWeights(indexes,
				Map.of(marshmallowPeaks.toString(), new double[] { 6.0D, 14.0D }),
				Map.of("COLD", new double[] { 8.0D, 0.0D }),
				type -> Set.of(ResourceKey.create(Registries.BIOME, marshmallowPeaks)));

		assertEquals(15.0D, weights.get(marshmallowPeaks)[0]);
		assertEquals(15.0D, weights.get(marshmallowPeaks)[1]);
	}

	@Test
	void savedWorldBoundaryUsesItsConfiguredBiomeInsteadOfEqualFallbackWeights() {
		BakedGeomeConfig config = observedWorldConfig();
		GeomeGeology geology = new GeomeGeology(-4965128775892001975L, config);
		double[] leftScores = new double[config.geomeCount()];
		double[] rightScores = new double[config.geomeCount()];

		assertEquals(1, geology.classifyColumn(null, WINDSWEPT_HILLS, 225, -261, leftScores));
		assertEquals(1, geology.classifyColumn(null, WINDSWEPT_HILLS, 226, -261, rightScores));
	}

	@Test
	void closeGeomeContestDoesNotMoveEveryStableLayerAtOneColumnBoundary() {
		// These are the leading scores measured in New World 5 at z=-261. The
		// fallback configuration changed winner between x=225 and x=226.
		double[] leftScores = { 2.618658D, 2.618126D };
		double[] rightScores = { 2.619946D, 2.620774D };
		int changedLayers = 0;
		for (int layer = -8; layer < 8; layer++) {
			int left = GeomeGeology.pickStableLayerGeome(leftScores, 0, 1, layer, 37);
			int right = GeomeGeology.pickStableLayerGeome(rightScores, 0, 1, layer, 37);
			if (left != right) {
				changedLayers++;
			}
		}

		assertTrue(changedLayers < 16,
				"all stable layers changed geome together across the observed x=225/226 boundary");
	}

	@Test
	void transitionBandUsesBothGeomesButKeepsClearDominanceOutsideIt() {
		boolean sawFirst = false;
		boolean sawSecond = false;
		for (int layer = 0; layer < 16; layer++) {
			int selected = GeomeGeology.pickStableLayerGeome(new double[] { 2.0D, 2.0D },
					0, 1, layer, 91);
			sawFirst |= selected == 0;
			sawSecond |= selected == 1;
		}

		assertTrue(sawFirst && sawSecond, "a tied geome boundary should be staggered by stable layer");
		assertEquals(0, GeomeGeology.pickStableLayerGeome(new double[] { 2.2D, 2.0D }, 0, 1, 3, 91));
		assertEquals(1, GeomeGeology.pickStableLayerGeome(new double[] { 2.0D, 2.2D }, 0, 1, 3, 91));
	}

	private static BakedGeomeConfig config(Map<ResourceLocation, double[]> biomeWeightsById) {
		double[] familyWeights = { 1.0D, 1.0D, 1.0D, 1.0D };
		GeomeDefinition[] geomes = {
				new GeomeDefinition("orespawn:first", 1.0D, familyWeights.clone()),
				new GeomeDefinition("orespawn:second", 1.0D, familyWeights.clone())
		};
		RockEntry[] rocks = {
				new RockEntry(Blocks.STONE.defaultBlockState(), RockFamily.SEDIMENTARY,
						64, 64, -64, 319, 1.0D, true, new double[] { 1.0D, 1.0D })
		};
		FormationSettings formations = new FormationSettings(FormationSettings.Algorithm.STABLE_LAYERS,
				256.0D, 100.0D, 8, 48.0D, 64.0D, 12.0D, 2, 0.85D);
		return new BakedGeomeConfig(geomes, 384.0D, 1.15D, 0.9D, 0.45D,
				Collections.emptyMap(), biomeWeightsById, rocks, formations);
	}

	private static BakedGeomeConfig observedWorldConfig() {
		String[] names = {
				"stable_craton", "mountain_belt", "volcanic_arc", "sedimentary_basin",
				"coastal_shelf", "arid_basin", "wetland_basin", "glacial_highland"
		};
		double[] bases = { 1.0D, 1.0D, 0.9D, 1.0D, 0.9D, 0.9D, 0.8D, 0.8D };
		GeomeDefinition[] geomes = new GeomeDefinition[names.length];
		Map<String, Integer> indexes = new LinkedHashMap<>();
		for (int i = 0; i < names.length; i++) {
			String id = "orespawn:" + names[i];
			indexes.put(id, i);
			geomes[i] = new GeomeDefinition(id, bases[i], new double[] { 1.0D, 1.0D, 1.0D, 1.0D });
		}
		double[] rule = new double[names.length];
		rule[0] = 1.0D;
		rule[1] = 4.0D;
		Map<ResourceLocation, double[]> biomeWeights = GeomeConfig.bakeBiomeIdentifierWeights(indexes,
				Map.of(WINDSWEPT_HILLS.toString(), rule));
		double[] rockWeights = new double[names.length];
		java.util.Arrays.fill(rockWeights, 1.0D);
		RockEntry[] rocks = {
				new RockEntry(Blocks.STONE.defaultBlockState(), RockFamily.SEDIMENTARY,
						64, 64, -64, 319, 1.0D, true, rockWeights)
		};
		FormationSettings formations = new FormationSettings(FormationSettings.Algorithm.STABLE_LAYERS,
				256.0D, 100.0D, 8, 48.0D, 64.0D, 12.0D, 2, 0.85D);
		return new BakedGeomeConfig(geomes, 384.0D, 1.15D, 0.9D, 0.45D,
				Collections.emptyMap(), biomeWeights, rocks, formations);
	}
}
