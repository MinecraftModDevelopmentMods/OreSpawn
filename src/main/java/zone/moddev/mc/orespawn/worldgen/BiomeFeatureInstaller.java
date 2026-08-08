package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;

/**
 * Adds OreSpawn's dynamic features to code-registered palette biomes, which do
 * not necessarily pass through Forge's BiomeLoadingEvent.
 */
final class BiomeFeatureInstaller {
	private static final Map<Biome, BiomeGenerationSettings> ORIGINALS =
			new IdentityHashMap<>();

	private BiomeFeatureInstaller() {
	}

	static void install(BakedBiomeWorldgen config, ResourceKey<Level> dimension) {
		if (config == null || WorldgenBenchmark.isVanillaBaseline()) return;
		Set<Biome> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Palette palette : config.palettes) {
			for (Entry entry : palette.entries) {
				Biome biome = entry.biome;
				if (visited.add(biome)) install(biome, dimension);
			}
		}
	}

	static void restoreAll() {
		for (Map.Entry<Biome, BiomeGenerationSettings> entry : ORIGINALS.entrySet()) {
			entry.getKey().generationSettings = entry.getValue();
		}
		ORIGINALS.clear();
	}

	private static void install(Biome biome, ResourceKey<Level> dimension) {
		BiomeGenerationSettings original = biome.getGenerationSettings();
		List<List<Supplier<ConfiguredFeature<?, ?>>>> features = copyFeatures(original);
		boolean changed = false;

		List<Supplier<ConfiguredFeature<?, ?>>> underground =
				step(features, GenerationStep.Decoration.UNDERGROUND_ORES);
		changed |= VanillaOreFeatureGate.wrapFeatureList(underground);
		if (GeomeConfig.hasTerrainReplacement(dimension)) {
			changed |= StoneReplacer.removeVanillaMatchingStoneFeatures(underground);
		}
		changed |= addUnique(underground, StoneReplacer.configuredFeature());
		changed |= addUnique(underground, OreSpawnOreGeneration.configuredFeature());
		changed |= addUnique(underground, FluidDepositFeature.configuredFeature());

		List<Supplier<ConfiguredFeature<?, ?>>> undergroundDecoration =
				step(features, GenerationStep.Decoration.UNDERGROUND_DECORATION);
		changed |= VanillaOreFeatureGate.wrapFeatureList(undergroundDecoration);

		changed |= installSurfaceStages(features);

		if (!changed) return;
		ORIGINALS.putIfAbsent(biome, original);
		biome.generationSettings = rebuild(original, features);
	}

	static boolean installSurfaceStages(List<List<Supplier<ConfiguredFeature<?, ?>>>> features) {
		List<Supplier<ConfiguredFeature<?, ?>>> local =
				step(features, GenerationStep.Decoration.LOCAL_MODIFICATIONS);
		List<Supplier<ConfiguredFeature<?, ?>>> top =
				step(features, GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		boolean changed = addUnique(local, BiomeSurfaceFeature.configuredFeature());
		changed |= addUnique(top, FlatBedrockFeature.configuredFeature());
		return changed;
	}

	private static List<List<Supplier<ConfiguredFeature<?, ?>>>> copyFeatures(
			BiomeGenerationSettings settings) {
		List<List<Supplier<ConfiguredFeature<?, ?>>>> copy = new ArrayList<>();
		for (List<Supplier<ConfiguredFeature<?, ?>>> featureStep : settings.features()) {
			copy.add(new ArrayList<>(featureStep));
		}
		return copy;
	}

	private static List<Supplier<ConfiguredFeature<?, ?>>> step(
			List<List<Supplier<ConfiguredFeature<?, ?>>>> features, GenerationStep.Decoration step) {
		while (features.size() <= step.ordinal()) features.add(new ArrayList<>());
		return features.get(step.ordinal());
	}

	private static boolean addUnique(List<Supplier<ConfiguredFeature<?, ?>>> features,
			ConfiguredFeature<?, ?> feature) {
		if (feature == null || features.stream().anyMatch(existing ->
				existing.get() == feature)) return false;
		features.add(() -> feature);
		return true;
	}

	private static BiomeGenerationSettings rebuild(BiomeGenerationSettings original,
			List<List<Supplier<ConfiguredFeature<?, ?>>>> features) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder();
		builder.surfaceBuilder(original.getSurfaceBuilder());
		for (GenerationStep.Carving carving : GenerationStep.Carving.values()) {
			for (Supplier<ConfiguredWorldCarver<?>> carver : original.getCarvers(carving)) {
				builder.addCarver(carving, carver.get());
			}
		}
		for (int step = 0; step < features.size(); step++) {
			for (Supplier<ConfiguredFeature<?, ?>> feature : features.get(step)) {
				builder.addFeature(step, feature);
			}
		}
		for (Supplier<ConfiguredStructureFeature<?, ?>> structure : original.structures()) {
			builder.addStructureStart(structure.get());
		}
		return builder.build();
	}
}
