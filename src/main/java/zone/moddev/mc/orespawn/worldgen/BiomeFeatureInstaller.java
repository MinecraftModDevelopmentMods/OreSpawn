package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

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
				Biome biome = entry.biome.value();
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
		List<List<Holder<PlacedFeature>>> features = copyFeatures(original);
		boolean changed = false;

		List<Holder<PlacedFeature>> underground =
				step(features, GenerationStep.Decoration.UNDERGROUND_ORES);
		changed |= VanillaOreFeatureGate.wrapFeatureList(underground);
		if (GeomeConfig.hasTerrainReplacement(dimension)) {
			changed |= StoneReplacer.removeVanillaMatchingStoneFeatures(underground);
		}
		changed |= addUnique(underground, StoneReplacer.placedFeature());
		changed |= addUnique(underground, OreSpawnOreGeneration.placedFeature());
		changed |= addUnique(underground, FluidDepositFeature.placedFeature());

		List<Holder<PlacedFeature>> undergroundDecoration =
				step(features, GenerationStep.Decoration.UNDERGROUND_DECORATION);
		changed |= VanillaOreFeatureGate.wrapFeatureList(undergroundDecoration);

		List<Holder<PlacedFeature>> top =
				step(features, GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		changed |= addUnique(top, FlatBedrockFeature.placedFeature());
		changed |= addUnique(top, BiomeSurfaceFeature.placedFeature());

		if (!changed) return;
		ORIGINALS.putIfAbsent(biome, original);
		biome.generationSettings = rebuild(original, features);
	}

	private static List<List<Holder<PlacedFeature>>> copyFeatures(
			BiomeGenerationSettings settings) {
		List<List<Holder<PlacedFeature>>> copy = new ArrayList<>();
		for (HolderSet<PlacedFeature> featureStep : settings.features()) {
			List<Holder<PlacedFeature>> values = new ArrayList<>();
			featureStep.forEach(values::add);
			copy.add(values);
		}
		return copy;
	}

	private static List<Holder<PlacedFeature>> step(
			List<List<Holder<PlacedFeature>>> features, GenerationStep.Decoration step) {
		while (features.size() <= step.ordinal()) features.add(new ArrayList<>());
		return features.get(step.ordinal());
	}

	private static boolean addUnique(List<Holder<PlacedFeature>> features,
			Holder<PlacedFeature> feature) {
		if (feature == null || features.stream().anyMatch(existing ->
				existing.value() == feature.value())) return false;
		features.add(feature);
		return true;
	}

	private static BiomeGenerationSettings rebuild(BiomeGenerationSettings original,
			List<List<Holder<PlacedFeature>>> features) {
		BiomeGenerationSettings.Builder builder = new BiomeGenerationSettings.Builder();
		for (GenerationStep.Carving carving : GenerationStep.Carving.values()) {
			for (Holder<ConfiguredWorldCarver<?>> carver : original.getCarvers(carving)) {
				builder.addCarver(carving, carver);
			}
		}
		for (int step = 0; step < features.size(); step++) {
			for (Holder<PlacedFeature> feature : features.get(step)) {
				builder.addFeature(step, feature);
			}
		}
		return builder.build();
	}
}
