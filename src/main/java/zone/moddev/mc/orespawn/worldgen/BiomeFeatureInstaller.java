package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Reversibly mutates Forge 25's static biome feature lists. Later versions
 * rebuild immutable generation settings; Minecraft 1.13 exposes the stage
 * lists directly instead.
 */
final class BiomeFeatureInstaller {
	private static final Map<Biome, List<List<CompositeFeature<?, ?>>>> ORIGINALS =
			new IdentityHashMap<>();

	private BiomeFeatureInstaller() {
	}

	static void install(BakedBiomeWorldgen config, ResourceLocation dimension) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		boolean terrain = GeomeConfig.hasTerrainReplacement(dimension);
		boolean managedOres = OreSpawnOreGeneration.hasManagedOres(dimension);
		boolean fluidDeposits = FluidDepositFeature.hasDeposits(dimension);
		boolean vanillaOreGate = OreSpawnOreGeneration.needsVanillaOreGate(dimension);
		boolean surfaces = config != null && config.hasSurfaces();
		boolean flatBedrock = FlatBedrockFeature.enabledFor(dimension);
		if (!terrain && !managedOres && !fluidDeposits && !vanillaOreGate
				&& !surfaces && !flatBedrock) return;
		for (Biome biome : ForgeRegistries.BIOMES.getValues()) {
			installFeatures(biome, terrain, managedOres, fluidDeposits,
					vanillaOreGate, surfaces, flatBedrock);
		}
	}

	static void restoreAll() {
		for (Map.Entry<Biome, List<List<CompositeFeature<?, ?>>>> saved : ORIGINALS.entrySet()) {
			Biome biome = saved.getKey();
			GenerationStage.Decoration[] stages = GenerationStage.Decoration.values();
			for (int index = 0; index < stages.length; index++) {
				List<CompositeFeature<?, ?>> live = biome.getFeatures(stages[index]);
				live.clear();
				live.addAll(saved.getValue().get(index));
			}
		}
		ORIGINALS.clear();
	}

	static void installFeatures(Biome biome, boolean terrain,
			boolean managedOres, boolean fluidDeposits, boolean vanillaOreGate,
			boolean surfaces, boolean flatBedrock) {
		ORIGINALS.computeIfAbsent(biome, BiomeFeatureInstaller::snapshot);
		if (terrain) {
			for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
				VanillaSpringCompatibility.rewriteFeatureList(biome.getFeatures(stage));
			}
		}

		List<CompositeFeature<?, ?>> underground =
				biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_ORES);
		if (vanillaOreGate) VanillaOreFeatureGate.wrapFeatureList(underground);
		if (terrain) {
			StoneReplacer.removeVanillaMatchingStoneFeatures(underground);
		}
		if (managedOres) addUnique(underground, OreSpawnOreGeneration.configuredFeature());
		if (fluidDeposits) addUnique(underground, FluidDepositFeature.configuredFeature());

		if (vanillaOreGate) {
			VanillaOreFeatureGate.wrapFeatureList(
					biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_DECORATION));
		}
		installSurfaceStages(biome, terrain, surfaces, flatBedrock);
	}

	static boolean installSurfaceStages(Biome biome, boolean terrain,
			boolean surfaces, boolean flatBedrock) {
		List<CompositeFeature<?, ?>> local =
				biome.getFeatures(GenerationStage.Decoration.LOCAL_MODIFICATIONS);
		boolean changed = false;
		if (terrain) {
			changed |= placeUniqueAt(local, StoneReplacer.configuredFeature(), 0);
			if (surfaces) {
				changed |= placeUniqueAt(local, BiomeSurfaceFeature.configuredFeature(), 1);
			}
		} else if (surfaces) {
			changed |= addUnique(local, BiomeSurfaceFeature.configuredFeature());
		}
		changed |= flatBedrock && addUnique(biome.getFeatures(
				GenerationStage.Decoration.TOP_LAYER_MODIFICATION),
				FlatBedrockFeature.configuredFeature());
		return changed;
	}

	private static List<List<CompositeFeature<?, ?>>> snapshot(Biome biome) {
		List<List<CompositeFeature<?, ?>>> result = new ArrayList<>();
		for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
			result.add(new ArrayList<>(biome.getFeatures(stage)));
		}
		return result;
	}

	private static boolean addUnique(List<CompositeFeature<?, ?>> features,
			CompositeFeature<?, ?> feature) {
		if (feature == null || features.contains(feature)) return false;
		features.add(feature);
		return true;
	}

	private static boolean placeUniqueAt(List<CompositeFeature<?, ?>> features,
			CompositeFeature<?, ?> feature, int index) {
		if (feature == null) return false;
		int current = features.indexOf(feature);
		int target = Math.min(index, features.size() - (current >= 0 ? 1 : 0));
		if (current == target) return false;
		if (current >= 0) features.remove(current);
		features.add(target, feature);
		return true;
	}
}
