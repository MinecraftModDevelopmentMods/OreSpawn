package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Fluids;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class NativeAquaticCompatibilityTest {
    @BeforeAll static void bootstrap() { Forge25TestBootstrap.registerVanilla(); }
    @Test void fluidOnlyCompatibilityDoesNotChangeSpringsOrUnrelatedFeatures() {
        CompositeFeature<?, ?> kelp = Biome.createCompositeFeature(Feature.KELP, new NoFeatureConfig(), Biome.PASSTHROUGH, IPlacementConfig.NO_PLACEMENT_CONFIG);
        CompositeFeature<?, ?> spring = Biome.createCompositeFeature(Feature.LIQUIDS, new LiquidsConfig(Fluids.WATER), Biome.PASSTHROUGH, IPlacementConfig.NO_PLACEMENT_CONFIG);
        List<CompositeFeature<?, ?>> features = new ArrayList<>(java.util.Arrays.asList(kelp, spring));
        assertTrue(VanillaSpringCompatibility.rewriteFeatureList(features, false, true));
        assertSame(NativeAquaticCompatibility.FEATURE, features.get(0).getFeature());
        assertSame(spring, features.get(1));
        assertFalse(VanillaSpringCompatibility.rewriteFeatureList(features, false, true));
        assertSame(Feature.KELP, kelp.getFeature(), "The saved original must remain unchanged");
    }
    @Test void onlyKnownNativeAquaticDecoratorsReceiveTheNativeView() {
        for (Feature<?> feature : new Feature<?>[]{Feature.LAKES, Feature.SEA_GRASS, Feature.KELP, Feature.SEA_PICKLE,
                Feature.CORAL_TREE, Feature.CORAL_CLAW, Feature.CORAL_MUSHROOM, Feature.SWAMP_TREE,
                Feature.ICEBERG, Feature.BLUE_ICE, Feature.OCEAN_MONUMENT, Feature.OCEAN_RUIN,
                Feature.BURIED_TREASURE, Feature.STRONGHOLD, Feature.VILLAGE, Feature.WATERLILY, Feature.REED,
                Feature.SPHERE_REPLACE, Feature.ICE_AND_SNOW}) assertTrue(NativeAquaticCompatibility.accepts(feature));
        for (Feature<?> feature : new Feature<?>[]{Feature.LIQUIDS, Feature.MINABLE,
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
