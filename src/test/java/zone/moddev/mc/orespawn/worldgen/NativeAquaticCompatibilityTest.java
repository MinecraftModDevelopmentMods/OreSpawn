package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.fluid.Fluids;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;

class NativeAquaticCompatibilityTest {
    @BeforeAll static void bootstrap() { Forge31TestBootstrap.registerVanilla(); }
    @Test void fluidOnlyCompatibilityDoesNotChangeSpringsOrUnrelatedFeatures() {
        ConfiguredFeature<?, ?> kelp = Feature.KELP.withConfiguration(new NoFeatureConfig()).withPlacement(net.minecraft.world.gen.placement.Placement.NOPE.configure(IPlacementConfig.NO_PLACEMENT_CONFIG));
        ConfiguredFeature<?, ?> spring = Feature.SPRING_FEATURE.withConfiguration(net.minecraft.world.biome.DefaultBiomeFeatures.WATER_SPRING_CONFIG).withPlacement(net.minecraft.world.gen.placement.Placement.NOPE.configure(IPlacementConfig.NO_PLACEMENT_CONFIG));
        List<ConfiguredFeature<?, ?>> features = new ArrayList<>(java.util.Arrays.asList(kelp, spring));
        assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features, false, true));
        assertSame(NativeAquaticCompatibility.FEATURE,
                ((net.minecraft.world.gen.feature.DecoratedFeatureConfig) features.get(0).config).feature.feature);
        assertSame(spring, features.get(1));
        assertFalse(VanillaSpringCompatibility.rewriteFeatureList(features, false, true));
        assertSame(Feature.KELP, ((net.minecraft.world.gen.feature.DecoratedFeatureConfig) kelp.config).feature.feature,
                "The saved original must remain unchanged");
    }
    @Test void onlyKnownNativeAquaticDecoratorsReceiveTheNativeView() {
        for (Feature<?> feature : new Feature<?>[]{Feature.LAKE, Feature.SEAGRASS, Feature.KELP, Feature.SEA_PICKLE,
                Feature.CORAL_TREE, Feature.CORAL_CLAW, Feature.CORAL_MUSHROOM, Feature.NORMAL_TREE,
                Feature.ICEBERG, Feature.BLUE_ICE, Feature.OCEAN_MONUMENT, Feature.OCEAN_RUIN,
                Feature.BURIED_TREASURE, Feature.STRONGHOLD, Feature.VILLAGE, Feature.RANDOM_PATCH,
                Feature.DISK, Feature.FREEZE_TOP_LAYER}) assertTrue(NativeAquaticCompatibility.accepts(feature));
        for (Feature<?> feature : new Feature<?>[]{Feature.SPRING_FEATURE, Feature.ORE,
                StoneReplacer.FEATURE, NativeAquaticCompatibility.FEATURE}) assertFalse(NativeAquaticCompatibility.accepts(feature));
    }

    @Test void nativeReadViewKeepsWritesAndRandomnessOnTheRealWorld() {
        BlockPos position = new BlockPos(-17, 40, 31);
        java.util.Random random = new java.util.Random(17);
        java.util.concurrent.atomic.AtomicReference<Object[]> write = new java.util.concurrent.atomic.AtomicReference<>();
        IWorld world = (IWorld) java.lang.reflect.Proxy.newProxyInstance(IWorld.class.getClassLoader(),
                new Class<?>[]{IWorld.class}, (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getBlockState": return Blocks.LAVA.getDefaultState();
                        case "getRandom": return random;
                        case "setBlockState": write.set(arguments); return true;
                        default: throw new AssertionError("Unexpected operation: " + method);
                    }
                });
        NativeAquiferView view = new NativeAquiferView(world, Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState());
        assertSame(Blocks.WATER.getDefaultState(), view.getBlockState(position));
        assertSame(Blocks.WATER.getDefaultState().getFluidState(), view.getFluidState(position));
        assertSame(random, view.getRandom());
        assertTrue(view.setBlockState(position, Blocks.LAVA.getDefaultState(), 2));
        assertSame(position, write.get()[0]);
        assertSame(Blocks.LAVA.getDefaultState(), write.get()[1]);
        assertEquals(2, write.get()[2]);
    }

    @Test void customWorldFluidLookupsAreNotReplacedWithBlockStateDefaults() {
        IWorld world = (IWorld) java.lang.reflect.Proxy.newProxyInstance(IWorld.class.getClassLoader(),
                new Class<?>[]{IWorld.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getBlockState")) return Blocks.STONE.getDefaultState();
                    if (method.getName().equals("getFluidState")) return Fluids.LAVA.getDefaultState();
                    throw new AssertionError("Unexpected operation: " + method);
                });
        NativeAquiferView view = new NativeAquiferView(world, Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState());
        assertSame(Fluids.LAVA.getDefaultState(), view.getFluidState(new BlockPos(0, 0, 0)));
    }
}
