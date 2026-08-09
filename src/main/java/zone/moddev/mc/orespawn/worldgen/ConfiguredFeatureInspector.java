package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.ConfiguredRandomFeatureList;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.MultipleRandomFeatureConfig;
import net.minecraft.world.gen.feature.MultipleWithChanceRandomFeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.SingleRandomFeature;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;

/** Traverses the inline configured-feature graphs used by Minecraft 1.15. */
final class ConfiguredFeatureInspector {
	private ConfiguredFeatureInspector() {
	}

	static boolean outputsAny(ConfiguredFeature<?, ?> feature, Block... blocks) {
		Set<ConfiguredFeature<?, ?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		return outputsAny(feature, visited, blocks);
	}

	static Block firstOreOutput(ConfiguredFeature<?, ?> feature) {
		Set<ConfiguredFeature<?, ?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		return firstOreOutput(feature, visited);
	}

	private static Block firstOreOutput(ConfiguredFeature<?, ?> feature,
			Set<ConfiguredFeature<?, ?>> visited) {
		if (feature == null || !visited.add(feature)) return null;
		Object config = feature.config;
		if (config instanceof OreFeatureConfig) return ((OreFeatureConfig) config).state.getBlock();
		if (config instanceof DecoratedFeatureConfig) {
			return firstOreOutput(((DecoratedFeatureConfig) config).feature, visited);
		}
		if (config instanceof MultipleRandomFeatureConfig) {
			MultipleRandomFeatureConfig choices = (MultipleRandomFeatureConfig) config;
			Block output = firstOreOutput(choices.defaultFeature, visited);
			if (output != null) return output;
			for (ConfiguredRandomFeatureList<?> choice : choices.features) {
				output = firstOreOutput(choice.feature, visited);
				if (output != null) return output;
			}
		}
		if (config instanceof MultipleWithChanceRandomFeatureConfig) {
			for (ConfiguredFeature<?, ?> choice :
					((MultipleWithChanceRandomFeatureConfig) config).features) {
				Block output = firstOreOutput(choice, visited);
				if (output != null) return output;
			}
		}
		if (config instanceof SingleRandomFeature) {
			for (ConfiguredFeature<?, ?> choice : ((SingleRandomFeature) config).features) {
				Block output = firstOreOutput(choice, visited);
				if (output != null) return output;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			Block output = firstOreOutput(choice.field_227285_a_, visited);
			return output != null ? output : firstOreOutput(choice.field_227286_b_, visited);
		}
		return null;
	}

	private static boolean outputsAny(ConfiguredFeature<?, ?> feature,
			Set<ConfiguredFeature<?, ?>> visited, Block[] blocks) {
		if (feature == null || !visited.add(feature)) return false;
		Object config = feature.config;
		if (config instanceof OreFeatureConfig) {
			Block output = ((OreFeatureConfig) config).state.getBlock();
			for (Block block : blocks) if (output == block) return true;
		}
		if (config instanceof DecoratedFeatureConfig) {
			return outputsAny(((DecoratedFeatureConfig) config).feature, visited, blocks);
		}
		if (config instanceof MultipleRandomFeatureConfig) {
			MultipleRandomFeatureConfig choices = (MultipleRandomFeatureConfig) config;
			if (outputsAny(choices.defaultFeature, visited, blocks)) return true;
			for (ConfiguredRandomFeatureList<?> choice : choices.features) {
				if (outputsAny(choice.feature, visited, blocks)) return true;
			}
		}
		if (config instanceof MultipleWithChanceRandomFeatureConfig) {
			for (ConfiguredFeature<?, ?> choice :
					((MultipleWithChanceRandomFeatureConfig) config).features) {
				if (outputsAny(choice, visited, blocks)) return true;
			}
		}
		if (config instanceof SingleRandomFeature) {
			for (ConfiguredFeature<?, ?> choice : ((SingleRandomFeature) config).features) {
				if (outputsAny(choice, visited, blocks)) return true;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			return outputsAny(choice.field_227285_a_, visited, blocks)
					|| outputsAny(choice.field_227286_b_, visited, blocks);
		}
		return false;
	}
}
