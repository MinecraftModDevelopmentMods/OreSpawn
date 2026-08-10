package zone.moddev.mc.orespawn.worldgen;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Runtime wrapper for Forge 25's inline vanilla ore features. */
public final class VanillaOreFeatureGate {
	private static final GateFeature GATE = new GateFeature();

	private VanillaOreFeatureGate() {
	}

	static void register() {
		// Configured features are inline objects in 1.13 and are wrapped when the
		// registered biome stage lists are installed.
	}

	static boolean wrapFeatureList(List<CompositeFeature<?, ?>> features) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			CompositeFeature<?, ?> original = features.get(index);
			if (original.getFeature() == GATE) continue;
			Block output = ConfiguredFeatureInspector.firstOreOutput(original);
			if (output == null) continue;
			features.set(index, ConfiguredFeatureInspector.replaceRoot(
					original, GATE, new GateConfig(original.getFeature(),
							ConfiguredFeatureInspector.featureConfig(original), output)));
			changed = true;
		}
		return changed;
	}

	private static final class GateFeature extends ContextFeature<GateConfig> {
		GateFeature() {
			super();
		}

		@Override
		boolean place(FeaturePlaceContext<GateConfig> context) {
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
			if (OreSpawnOreGeneration.takesOverVanillaOre(
					WorldIds.dimension(context.level()), context.config().output)) return false;
			return ConfiguredFeatureInspector.placeRoot(context.config().delegateFeature,
					context.config().delegateConfig, context);
		}
	}

	private static final class GateConfig implements IFeatureConfig {
		final Feature<?> delegateFeature;
		final IFeatureConfig delegateConfig;
		final Block output;

		GateConfig(Feature<?> delegateFeature, IFeatureConfig delegateConfig, Block output) {
			this.delegateFeature = delegateFeature;
			this.delegateConfig = delegateConfig;
			this.output = output;
		}

	}
}
