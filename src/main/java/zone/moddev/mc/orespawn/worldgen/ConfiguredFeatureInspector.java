package zone.moddev.mc.orespawn.worldgen;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.MinableConfig;
import net.minecraft.world.gen.feature.RandomDefaultFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureWithConfigConfig;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;
import net.minecraft.world.gen.placement.BasePlacement;
import net.minecraft.world.gen.placement.IPlacementConfig;

/** Traverses the inline composite-feature graphs used by Minecraft 1.13. */
final class ConfiguredFeatureInspector {
	private static final Field FEATURE_CONFIG = field(IFeatureConfig.class);
	private static final Field BASE_PLACEMENT = field(BasePlacement.class);
	private static final Field PLACEMENT_CONFIG = field(IPlacementConfig.class);

	private ConfiguredFeatureInspector() {
	}

	static boolean outputsAny(CompositeFeature<?, ?> feature, Block... blocks) {
		return outputsAny(feature.getFeature(), featureConfig(feature),
				Collections.newSetFromMap(new IdentityHashMap<>()), blocks);
	}

	static Block firstOreOutput(CompositeFeature<?, ?> feature) {
		return firstOreOutput(feature.getFeature(), featureConfig(feature),
				Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	static IFeatureConfig featureConfig(CompositeFeature<?, ?> feature) {
		return (IFeatureConfig) get(FEATURE_CONFIG, feature);
	}

	static Object basePlacement(CompositeFeature<?, ?> feature) {
		return get(BASE_PLACEMENT, feature);
	}

	static Object placementConfig(CompositeFeature<?, ?> feature) {
		return get(PLACEMENT_CONFIG, feature);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static CompositeFeature<?, ?> replaceRoot(CompositeFeature<?, ?> original,
			Feature<?> feature, IFeatureConfig config) {
		return new CompositeFeature((Feature) feature, config,
				(BasePlacement) get(BASE_PLACEMENT, original),
				(IPlacementConfig) get(PLACEMENT_CONFIG, original));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static boolean placeRoot(Feature<?> feature, IFeatureConfig config, FeaturePlaceContext<?> context) {
		return ((Feature) feature).func_212245_a(context.level(), context.chunkGenerator(), context.random(),
				context.origin(), config);
	}

	private static Block firstOreOutput(Feature<?> feature, IFeatureConfig config,
			Set<Object> visited) {
		if (feature == null || config == null || !visited.add(config)) return null;
		if (config instanceof MinableConfig) return ((MinableConfig) config).state.getBlock();
		if (feature instanceof CompositeFeature) {
			CompositeFeature<?, ?> nested = (CompositeFeature<?, ?>) feature;
			return firstOreOutput(nested.getFeature(), featureConfig(nested), visited);
		}
		if (config instanceof RandomDefaultFeatureListConfig) {
			RandomDefaultFeatureListConfig choices = (RandomDefaultFeatureListConfig) config;
			for (int index = 0; index < choices.field_202449_a.length; index++) {
				Block output = firstOreOutput(choices.field_202449_a[index],
						choices.field_202450_b[index], visited);
				if (output != null) return output;
			}
			return firstOreOutput(choices.field_202452_d, choices.field_202453_f, visited);
		}
		if (config instanceof RandomFeatureListConfig) {
			RandomFeatureListConfig choices = (RandomFeatureListConfig) config;
			for (int index = 0; index < choices.field_202454_a.length; index++) {
				Block output = firstOreOutput(choices.field_202454_a[index],
						choices.field_202455_b[index], visited);
				if (output != null) return output;
			}
		}
		if (config instanceof RandomFeatureWithConfigConfig) {
			RandomFeatureWithConfigConfig choices = (RandomFeatureWithConfigConfig) config;
			for (int index = 0; index < choices.features.length; index++) {
				Block output = firstOreOutput(choices.features[index], choices.configs[index], visited);
				if (output != null) return output;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			Block output = firstOreOutput(choice.field_202445_a, choice.field_202446_b, visited);
			return output != null ? output
					: firstOreOutput(choice.field_202447_c, choice.field_202448_d, visited);
		}
		return null;
	}

	private static boolean outputsAny(Feature<?> feature, IFeatureConfig config,
			Set<Object> visited, Block[] blocks) {
		if (feature == null || config == null || !visited.add(config)) return false;
		if (config instanceof MinableConfig) {
			Block output = ((MinableConfig) config).state.getBlock();
			for (Block block : blocks) if (output == block) return true;
		}
		if (feature instanceof CompositeFeature) {
			CompositeFeature<?, ?> nested = (CompositeFeature<?, ?>) feature;
			return outputsAny(nested.getFeature(), featureConfig(nested), visited, blocks);
		}
		if (config instanceof RandomDefaultFeatureListConfig) {
			RandomDefaultFeatureListConfig choices = (RandomDefaultFeatureListConfig) config;
			for (int index = 0; index < choices.field_202449_a.length; index++) {
				if (outputsAny(choices.field_202449_a[index], choices.field_202450_b[index], visited, blocks))
					return true;
			}
			return outputsAny(choices.field_202452_d, choices.field_202453_f, visited, blocks);
		}
		if (config instanceof RandomFeatureListConfig) {
			RandomFeatureListConfig choices = (RandomFeatureListConfig) config;
			for (int index = 0; index < choices.field_202454_a.length; index++) {
				if (outputsAny(choices.field_202454_a[index], choices.field_202455_b[index], visited, blocks))
					return true;
			}
		}
		if (config instanceof RandomFeatureWithConfigConfig) {
			RandomFeatureWithConfigConfig choices = (RandomFeatureWithConfigConfig) config;
			for (int index = 0; index < choices.features.length; index++) {
				if (outputsAny(choices.features[index], choices.configs[index], visited, blocks)) return true;
			}
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			return outputsAny(choice.field_202445_a, choice.field_202446_b, visited, blocks)
					|| outputsAny(choice.field_202447_c, choice.field_202448_d, visited, blocks);
		}
		return false;
	}

	private static Field field(Class<?> type) {
		for (Field field : CompositeFeature.class.getDeclaredFields()) {
			if (field.getType() == type || type.isAssignableFrom(field.getType())) {
				field.setAccessible(true);
				return field;
			}
		}
		throw new IllegalStateException("Minecraft 1.13 CompositeFeature field missing: " + type.getName());
	}

	private static Object get(Field field, Object owner) {
		try {
			return field.get(owner);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Cannot inspect Minecraft 1.13 CompositeFeature", e);
		}
	}
}
