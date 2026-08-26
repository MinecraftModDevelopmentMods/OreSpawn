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

import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeGenerationSettings;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.carver.ConfiguredCarver;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.StructureFeature;

/**
 * Adds OreSpawn's dynamic features to code-registered palette biomes, which do
 * not necessarily pass through Forge's BiomeLoadingEvent.
 */
final class BiomeFeatureInstaller {
	private static final Map<Biome, BiomeGenerationSettings> ORIGINALS =
			new IdentityHashMap<>();

	private BiomeFeatureInstaller() {
	}

	static void install(BakedBiomeWorldgen config, RegistryKey<World> dimension) {
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

	private static void install(Biome biome, RegistryKey<World> dimension) {
		BiomeGenerationSettings original = biome.getGenerationSettings();
		List<List<Supplier<ConfiguredFeature<?, ?>>>> features = copyFeatures(original);
		boolean changed = false;

		List<Supplier<ConfiguredFeature<?, ?>>> underground =
				step(features, GenerationStage.Decoration.UNDERGROUND_ORES);
		changed |= VanillaOreFeatureGate.wrapFeatureList(underground);
		boolean terrain = GeomeConfig.hasTerrainReplacement(dimension);
		if (terrain) {
			changed |= StoneReplacer.removeVanillaMatchingStoneFeatures(underground);
		}
		changed |= addUnique(underground, OreSpawnOreGeneration.configuredFeature());
		changed |= addUnique(underground, FluidDepositFeature.configuredFeature());

		List<Supplier<ConfiguredFeature<?, ?>>> undergroundDecoration =
				step(features, GenerationStage.Decoration.UNDERGROUND_DECORATION);
		changed |= VanillaOreFeatureGate.wrapFeatureList(undergroundDecoration);

		changed |= installSurfaceStages(features, terrain, true);

		if (!changed) return;
		ORIGINALS.putIfAbsent(biome, original);
		biome.generationSettings = rebuild(original, features);
	}

	static boolean installSurfaceStages(List<List<Supplier<ConfiguredFeature<?, ?>>>> features,
			boolean terrain, boolean surfaces) {
		List<Supplier<ConfiguredFeature<?, ?>>> local =
				step(features, GenerationStage.Decoration.LOCAL_MODIFICATIONS);
		List<Supplier<ConfiguredFeature<?, ?>>> top =
				step(features, GenerationStage.Decoration.TOP_LAYER_MODIFICATION);
		boolean changed = false;
		if (terrain) {
			changed |= StoneReplacer.placeUniqueAt(local, StoneReplacer.configuredFeature(), 0);
			if (surfaces) {
				changed |= StoneReplacer.placeUniqueAt(local,
						BiomeSurfaceFeature.configuredFeature(), 1);
			}
		} else if (surfaces) {
			changed |= addUnique(local, BiomeSurfaceFeature.configuredFeature());
		}
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
			List<List<Supplier<ConfiguredFeature<?, ?>>>> features, GenerationStage.Decoration step) {
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
		for (GenerationStage.Carving carving : GenerationStage.Carving.values()) {
			for (Supplier<ConfiguredCarver<?>> carver : original.getCarvers(carving)) {
				builder.addCarver(carving, carver.get());
			}
		}
		for (int step = 0; step < features.size(); step++) {
			for (Supplier<ConfiguredFeature<?, ?>> feature : features.get(step)) {
				builder.addFeature(step, feature);
			}
		}
		for (Supplier<StructureFeature<?, ?>> structure : original.structures()) {
			builder.addStructureStart(structure.get());
		}
		return builder.build();
	}
}
