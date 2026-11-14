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
		// Minecraft 1.13 stores features inline; wrap them when installing the biome's stage lists.
	}

	static Block wrappedOutput(Feature<?> feature, IFeatureConfig config) {
		return feature == GATE && config instanceof GateConfig ? ((GateConfig) config).output : null;
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
			WorldGeologyProfileManager.resolveFreshPoliciesBeforeGeneration();
			if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return false;
			net.minecraft.util.math.ChunkPos chunk = context.decorationChunk().getPos();
			if (!OreSpawnOreGeneration.allowsVanillaOre(WorldIds.dimension(context.level()),
					context.config().output, context.level().getSeed(), chunk.x, chunk.z)) return false;
			boolean previous = NativeStonePatchContext.enter(
					NaturalFormations.enabledForPlacement(context.level().getWorld()), context.config().output);
			try {
				return ConfiguredFeatureInspector.placeRoot(context.config().delegateFeature,
						context.config().delegateConfig, context);
			} finally {
				NativeStonePatchContext.restore(previous);
			}
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
