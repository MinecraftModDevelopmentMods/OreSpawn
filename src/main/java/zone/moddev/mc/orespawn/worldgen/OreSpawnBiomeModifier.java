package zone.moddev.mc.orespawn.worldgen;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeGenerationSettingsBuilder;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo.BiomeInfo;

/**
 * Attaches OreSpawn's profile-driven features to every biome. Each feature
 * performs a constant-time dimension/profile check before doing any work.
 */
public final class OreSpawnBiomeModifier implements BiomeModifier {
	public static final OreSpawnBiomeModifier INSTANCE = new OreSpawnBiomeModifier();
	public static final MapCodec<OreSpawnBiomeModifier> CODEC = MapCodec.unit(INSTANCE);

	private OreSpawnBiomeModifier() {
	}

	@Override
	public void modify(Holder<Biome> biome, Phase phase, BiomeInfo.Builder builder) {
		if (phase == Phase.ADD && !WorldgenBenchmark.isVanillaBaseline()) {
			apply(builder.getGenerationSettings());
		}
	}

	@Override
	public MapCodec<? extends BiomeModifier> codec() {
		return CODEC;
	}

	static boolean apply(BiomeGenerationSettingsBuilder generation) {
		boolean changed = false;
		List<Holder<PlacedFeature>> underground =
				generation.getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES);
		changed |= StoneReplacer.wrapVanillaMatchingStoneFeatures(underground);
		changed |= VanillaOreFeatureGate.wrapFeatureList(underground);
		changed |= addUnique(underground, StoneReplacer.placedFeature());
		changed |= addUnique(underground, OreSpawnOreGeneration.placedFeature());
		changed |= addUnique(underground, FluidDepositFeature.placedFeature());

		List<Holder<PlacedFeature>> undergroundDecoration =
				generation.getFeatures(GenerationStep.Decoration.UNDERGROUND_DECORATION);
		changed |= VanillaOreFeatureGate.wrapFeatureList(undergroundDecoration);

		List<Holder<PlacedFeature>> top =
				generation.getFeatures(GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
		changed |= addUnique(top, FlatBedrockFeature.placedFeature());
		changed |= addUnique(top, BiomeSurfaceFeature.placedFeature());
		return changed;
	}

	private static boolean addUnique(List<Holder<PlacedFeature>> features,
			Holder<PlacedFeature> feature) {
		if (feature == null || features.stream().anyMatch(existing ->
				existing.value() == feature.value())) return false;
		features.add(feature);
		return true;
	}
}
