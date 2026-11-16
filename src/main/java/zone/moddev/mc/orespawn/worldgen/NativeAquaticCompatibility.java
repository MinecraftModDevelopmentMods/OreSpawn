package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Preserves native decoration that checks for the exact water block, rather than its fluid. */
final class NativeAquaticCompatibility extends ContextFeature<NativeAquaticCompatibility.Config> {
    static final NativeAquaticCompatibility FEATURE = new NativeAquaticCompatibility();
    private NativeAquaticCompatibility() {
        super(data -> { throw new IllegalStateException("Native decoration wrappers are installed in biome feature lists"); });
    }
    static boolean accepts(Feature<?> feature) {
        return feature == Feature.LAKE || feature == Feature.SEAGRASS || feature == Feature.KELP || feature == Feature.SEA_PICKLE
                || feature == Feature.CORAL_TREE || feature == Feature.CORAL_CLAW || feature == Feature.CORAL_MUSHROOM
                || feature == Feature.NORMAL_TREE || feature == Feature.ICEBERG || feature == Feature.BLUE_ICE
                || feature == Feature.OCEAN_MONUMENT || feature == Feature.OCEAN_RUIN
                || feature == Feature.BURIED_TREASURE || feature == Feature.STRONGHOLD || feature == Feature.VILLAGE
                || feature == Feature.RANDOM_PATCH || feature == Feature.DISK
                || feature == Feature.FREEZE_TOP_LAYER;
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
        @Override public <T> com.mojang.datafixers.Dynamic<T> serialize(com.mojang.datafixers.types.DynamicOps<T> ops) {
            throw new UnsupportedOperationException("Native decoration wrappers are rebuilt from biome feature lists");
        }
    }
}
