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
			addUnique(underground, StoneReplacer.configuredFeature());
		}
		if (managedOres) addUnique(underground, OreSpawnOreGeneration.configuredFeature());
		if (fluidDeposits) addUnique(underground, FluidDepositFeature.configuredFeature());

		if (vanillaOreGate) {
			VanillaOreFeatureGate.wrapFeatureList(
					biome.getFeatures(GenerationStage.Decoration.UNDERGROUND_DECORATION));
		}
		installSurfaceStages(biome, surfaces, flatBedrock);
	}

	static boolean installSurfaceStages(Biome biome, boolean surfaces, boolean flatBedrock) {
		boolean changed = surfaces && addUnique(biome.getFeatures(
				GenerationStage.Decoration.LOCAL_MODIFICATIONS),
				BiomeSurfaceFeature.configuredFeature());
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
}
