package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.MinableConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;
import zone.moddev.mc.orespawn.util.ConfiguredBlockState;

class NativePortContractsTest {
    @BeforeAll static void bootstrap() { Forge25TestBootstrap.registerVanilla(); }

    @Test void retainedLegacyMetadataDoesNotReinterpretFlattenedHostIds() throws Exception {
        JsonObject host = new JsonObject();
        host.addProperty("block", "minecraft:stone"); host.addProperty("metadata", 3);
        JsonArray hosts = new JsonArray(); hosts.add(host);
        Map<Block, Double> blocks = new IdentityHashMap<>();
        Map<IBlockState, Double> exact = new IdentityHashMap<>();
        Method add = OreSpawnOreGeneration.class.getDeclaredMethod("addBlocks", Map.class, Map.class, com.google.gson.JsonElement.class);
        add.setAccessible(true); add.invoke(null, blocks, exact, hosts);
        assertEquals(java.util.Collections.singletonMap(Blocks.STONE, 1.0), blocks);
        assertTrue(exact.isEmpty(), "Existing 1.13 profiles used flattened IDs, not legacy variants");
    }

    @Test void exactPropertiesUseMinecraftValidationWithoutChangingTheDefinition() {
        JsonObject block = new JsonObject(); block.addProperty("block", "minecraft:oak_log");
        JsonObject properties = new JsonObject(); properties.addProperty("axis", "x");
        block.add("properties", properties);
        String original = block.toString();
        assertEquals(net.minecraft.util.EnumFacing.Axis.X,
                ConfiguredBlockState.read(block).get(net.minecraft.state.properties.BlockStateProperties.AXIS));
        assertEquals(original, block.toString());
        properties.addProperty("axis", "sideways");
        assertThrows(IllegalArgumentException.class, () -> ConfiguredBlockState.read(block));
    }

    @Test void oreGateDoesNotHideNativeStonePatchesFromTerrainReplacement() {
        CompositeFeature<?, ?> patch = Biome.createCompositeFeature(Feature.MINABLE,
                new MinableConfig(state -> true, Blocks.GRANITE.getDefaultState(), 10),
                Biome.PASSTHROUGH, IPlacementConfig.NO_PLACEMENT_CONFIG);
        List<CompositeFeature<?, ?>> features = new ArrayList<>(); features.add(patch);
        assertTrue(VanillaOreFeatureGate.wrapFeatureList(features));
        assertTrue(StoneReplacer.removeVanillaMatchingStoneFeatures(features));
        assertTrue(features.isEmpty());
    }

    @Test void nestedNativeStoneContextsRestoreTheirOuterPass() {
        boolean outer = NativeStonePatchContext.enter(true, Blocks.GRANITE);
        try {
            assertTrue(NativeStonePatchContext.active());
            boolean inner = NativeStonePatchContext.enter(true, Blocks.IRON_ORE);
            assertFalse(NativeStonePatchContext.active());
            NativeStonePatchContext.restore(inner);
            assertTrue(NativeStonePatchContext.active());
        } finally { NativeStonePatchContext.restore(outer); }
        assertFalse(NativeStonePatchContext.active());
    }
}
