package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.ConfiguredRandomFeatureList;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.MultipleRandomFeatureConfig;
import net.minecraft.world.gen.feature.MultipleWithChanceRandomFeatureConfig;
import net.minecraft.world.gen.feature.SingleRandomFeature;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;
import net.minecraft.world.server.ServerWorld;

/** Test-only package bridge for the registered Forge 28 spring wrapper. */
public final class SurfaceProbeSpringBridge {
	private SurfaceProbeSpringBridge() {
	}

	public static LiquidsConfig findRewrittenSpring(Iterable<Biome> biomes) {
		Set<ConfiguredFeature<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Biome biome : biomes) {
			for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
				for (ConfiguredFeature<?> feature : biome.getFeatures(stage)) {
					LiquidsConfig result = find(feature, visited);
					if (result != null) return result;
				}
			}
		}
		return null;
	}

	public static boolean recognizesProviderRock(Block block) {
		return VanillaSpringCompatibility.isHost(block);
	}

	public static boolean place(ServerWorld world, BlockPos pos, LiquidsConfig config) {
		return VanillaSpringCompatibility.FEATURE.place(world,
				world.getChunkProvider().getChunkGenerator(), world.getRandom(), pos, config);
	}

	private static LiquidsConfig find(ConfiguredFeature<?> feature,
			Set<ConfiguredFeature<?>> visited) {
		if (feature == null || !visited.add(feature)) return null;
		if (feature.feature == VanillaSpringCompatibility.FEATURE
				&& feature.config instanceof LiquidsConfig) return (LiquidsConfig) feature.config;
		Object config = feature.config;
		if (config instanceof DecoratedFeatureConfig) {
			return find(((DecoratedFeatureConfig) config).feature, visited);
		}
		if (config instanceof MultipleRandomFeatureConfig) {
			MultipleRandomFeatureConfig random = (MultipleRandomFeatureConfig) config;
			LiquidsConfig result = find(random.defaultFeature, visited);
			if (result != null) return result;
			for (ConfiguredRandomFeatureList<?> choice : random.features) {
				result = find(configured(choice), visited);
				if (result != null) return result;
			}
		}
		if (config instanceof MultipleWithChanceRandomFeatureConfig) {
			for (ConfiguredFeature<?> choice :
					((MultipleWithChanceRandomFeatureConfig) config).features) {
				LiquidsConfig result = find(choice, visited);
				if (result != null) return result;
			}
		}
		if (config instanceof SingleRandomFeature) {
			for (ConfiguredFeature<?> choice : ((SingleRandomFeature) config).features) {
				LiquidsConfig result = find(choice, visited);
				if (result != null) return result;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			LiquidsConfig result = find(choice.trueFeature, visited);
			return result != null ? result : find(choice.falseFeature, visited);
		}
		return null;
	}

	private static <FC extends net.minecraft.world.gen.feature.IFeatureConfig>
			ConfiguredFeature<FC> configured(ConfiguredRandomFeatureList<FC> choice) {
		return new ConfiguredFeature<>(choice.feature, choice.config);
	}
}
