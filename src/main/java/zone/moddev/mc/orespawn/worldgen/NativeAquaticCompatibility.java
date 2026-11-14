package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Preserves native decoration that checks for the exact water block, rather than its fluid. */
final class NativeAquaticCompatibility extends ContextFeature<NativeAquaticCompatibility.Config> {
    static final NativeAquaticCompatibility FEATURE = new NativeAquaticCompatibility();
    static boolean accepts(Feature<?> feature) {
        return feature == Feature.LAKES || feature == Feature.SEA_GRASS || feature == Feature.KELP || feature == Feature.SEA_PICKLE
                || feature == Feature.CORAL_TREE || feature == Feature.CORAL_CLAW || feature == Feature.CORAL_MUSHROOM
                || feature == Feature.SWAMP_TREE || feature == Feature.ICEBERG || feature == Feature.BLUE_ICE
                || feature == Feature.OCEAN_MONUMENT || feature == Feature.OCEAN_RUIN
                || feature == Feature.BURIED_TREASURE || feature == Feature.STRONGHOLD || feature == Feature.VILLAGE
                || feature == Feature.WATERLILY || feature == Feature.REED || feature == Feature.SPHERE_REPLACE
                || feature == Feature.ICE_AND_SNOW;
    }
    @Override boolean place(FeaturePlaceContext<Config> context) {
        return ConfiguredFeatureInspector.placeRoot(context.config().feature, context.config().config,
                new FeaturePlaceContext<>(AquiferMaterialSubstitution.nativeDecorationView(context.level()),
                        context.chunkGenerator(), context.random(), context.origin(), context.config()));
    }
    static final class Config implements IFeatureConfig {
        final Feature<?> feature;
        final IFeatureConfig config;
        Config(Feature<?> feature, IFeatureConfig config) { this.feature = feature; this.config = config; }
    }
}
