package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.gen.feature.*;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.world.biome.DefaultBiomeFeatures;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.LiquidsConfig;

final class VanillaSpringCompatibility {
	private static final Map<LiquidsConfig, Set<Block>> ORIGINAL_HOSTS = new IdentityHashMap<>();

	private VanillaSpringCompatibility() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	static synchronized void refresh(BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<>());
		if (config != null) {
			config.addRockBlocks(rocks);
		}
		refreshBlocks(rocks);
	}

	static synchronized void refreshBlocks(Iterable<Block> rocks) {
		update(DefaultBiomeFeatures.LAVA_SPRING_CONFIG, rocks);
		update(DefaultBiomeFeatures.WATER_SPRING_CONFIG, rocks);
	}

	static LiquidsConfig vanillaSpring(ConfiguredFeature<?, ?> root) {
		if (!(root.config instanceof LiquidsConfig)) {
			throw new IllegalStateException("Vanilla spring configuration is missing");
		}
		return (LiquidsConfig) root.config;
	}

	private static void update(LiquidsConfig spring, Iterable<Block> rocks) {
		Set<Block> original = ORIGINAL_HOSTS.computeIfAbsent(spring, ignored -> spring.acceptedBlocks);
		spring.acceptedBlocks = merge(original, rocks);
	}

	static Set<Block> merge(Set<Block> original, Iterable<Block> additionalBlocks) {
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		ImmutableSet.Builder<Block> merged = ImmutableSet.builder();
		for (Block block : original) {
			if (seen.add(block)) merged.add(block);
		}
		for (Block block : additionalBlocks) {
			if (seen.add(block)) merged.add(block);
		}
		// LiquidsConfig.CODEC casts this field to ImmutableSet in 1.15.2.
		return merged.build();
	}
    static boolean rewriteFeatureList(List<ConfiguredFeature<?, ?>> features) {
        return rewriteFeatureList(features, true, false);
    }

    static boolean rewriteFeatureList(List<ConfiguredFeature<?, ?>> features, boolean springs, boolean aquifers) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			ConfiguredFeature<?, ?> original = features.get(index);
			ConfiguredFeature<?, ?> rewritten = rewrite(original, springs, aquifers);
			if (rewritten != original) {
				features.set(index, rewritten);
				changed = true;
			}
		}
		return changed;
	}

	private static ConfiguredFeature<?, ?> rewrite(ConfiguredFeature<?, ?> root, boolean springs, boolean aquifers) {
		if (root == null) return null;
        if (springs && NaturalFormations.hasConfiguredFormations() && root.feature == Feature.LAKE
                && root.config instanceof net.minecraft.world.gen.feature.BlockStateFeatureConfig)
            return configured(VanillaLakeCompatibility.FEATURE, root.config);
        if (aquifers && NativeAquaticCompatibility.accepts(root.feature))
            return configured(NativeAquaticCompatibility.FEATURE,
                    new NativeAquaticCompatibility.Config(root.feature, (IFeatureConfig) root.config));


		Object config = root.config;
		if (config instanceof DecoratedFeatureConfig) {
			DecoratedFeatureConfig decorated = (DecoratedFeatureConfig) config;
			ConfiguredFeature<?, ?> child = rewrite(decorated.feature, springs, aquifers);
			return child == decorated.feature ? root
					: configured(root.feature, new DecoratedFeatureConfig(child, decorated.decorator));
		}
		if (config instanceof MultipleRandomFeatureConfig) {
			MultipleRandomFeatureConfig random = (MultipleRandomFeatureConfig) config;
			boolean changed = false;
			List<ConfiguredRandomFeatureList<?>> choices = new ArrayList<>(random.features.size());
			for (ConfiguredRandomFeatureList<?> choice : random.features) {
				ConfiguredFeature<?, ?> child = rewrite(choice.feature, springs, aquifers);
				changed |= child != choice.feature;
				choices.add(randomChoice(child, choice.chance));
			}
			ConfiguredFeature<?, ?> fallback = rewrite(random.defaultFeature, springs, aquifers);
			changed |= fallback != random.defaultFeature;
			return changed ? configured(root.feature,
					new MultipleRandomFeatureConfig(choices, fallback)) : root;
		}
		if (config instanceof MultipleWithChanceRandomFeatureConfig) {
			MultipleWithChanceRandomFeatureConfig random =
					(MultipleWithChanceRandomFeatureConfig) config;
			List<ConfiguredFeature<?, ?>> choices = rewrite(random.features, springs, aquifers);
			return choices == random.features ? root : configured(root.feature,
					new MultipleWithChanceRandomFeatureConfig(choices, random.count));
		}
		if (config instanceof SingleRandomFeature) {
			SingleRandomFeature random = (SingleRandomFeature) config;
			List<ConfiguredFeature<?, ?>> choices = rewrite(random.features, springs, aquifers);
			return choices == random.features ? root
					: configured(root.feature, new SingleRandomFeature(choices));
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			ConfiguredFeature<?, ?> whenTrue = rewrite(choice.field_227285_a_, springs, aquifers);
			ConfiguredFeature<?, ?> whenFalse = rewrite(choice.field_227286_b_, springs, aquifers);
			return whenTrue == choice.field_227285_a_ && whenFalse == choice.field_227286_b_ ? root
					: configured(root.feature, new TwoFeatureChoiceConfig(whenTrue, whenFalse));
		}
		return root;
	}

	private static List<ConfiguredFeature<?, ?>> rewrite(List<ConfiguredFeature<?, ?>> originals, boolean springs, boolean aquifers) {
		List<ConfiguredFeature<?, ?>> rewritten = null;
		for (int index = 0; index < originals.size(); index++) {
			ConfiguredFeature<?, ?> original = originals.get(index);
			ConfiguredFeature<?, ?> value = rewrite(original, springs, aquifers);
			if (value != original && rewritten == null) rewritten = new ArrayList<>(originals);
			if (rewritten != null) rewritten.set(index, value);
		}
		return rewritten == null ? originals : rewritten;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ConfiguredFeature<?, ?> configured(Feature<?> feature, Object config) {
		return new ConfiguredFeature((Feature) feature, (IFeatureConfig) config);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ConfiguredRandomFeatureList<?> randomChoice(
			ConfiguredFeature<?, ?> feature, Float chance) {
		return new ConfiguredRandomFeatureList(feature, chance);
	}

}
