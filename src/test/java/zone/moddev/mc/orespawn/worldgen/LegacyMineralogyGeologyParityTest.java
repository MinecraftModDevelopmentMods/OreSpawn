package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyMineralogyGeologyParityTest {
    private static final String SEALED_VECTOR_SHA256 =
            "FE97624A94338C9B7E6EE6EE018A48C13920C7E55012BDB5B05A8584DFA93F5C";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cyanoSamplerMatchesPublishedMineralogy540AndSealedVectors() throws Exception {
        Block[] igneous = { Blocks.STONE, Blocks.OBSIDIAN, Blocks.NETHERRACK };
        Block[] metamorphic = { Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE };
        Block[] sedimentary = { Blocks.SANDSTONE, Blocks.GRAVEL, Blocks.COAL_ORE,
                Blocks.SANDSTONE };
        MessageDigest sealed = MessageDigest.getInstance("SHA-256");

        String configuredPath = System.getProperty("orespawn.mineralogy5Oracle", "");
        Path oracle = configuredPath.trim().isEmpty() ? null : Paths.get(configuredPath);
        PublishedMineralogy published = oracle != null && Files.isRegularFile(oracle)
                ? PublishedMineralogy.open(oracle) : null;
        try {
            if (published != null) published.configure(9, igneous, metamorphic, sedimentary);
            for (long seed : new long[] { 0L, -4965128775892001975L }) {
                Geology os4 = new Geology(seed, 128.0D, 37.25D, 9, false,
                        states(igneous), states(metamorphic), states(sedimentary));
                PublishedSampler sampler = published == null ? null : published.newSampler(seed, 128.0D, 37.25D);
                for (int x : new int[] { -1025, -257, -1, 0, 1, 255, 1024 }) {
                    for (int z : new int[] { -1025, -257, -1, 0, 1, 255, 1024 }) {
                        for (int y = 0; y < 256; y += 7) {
                            Block actual = os4.getStoneAt(x, y, z);
                            update(sealed, seed, x, y, z, actual);
                            if (sampler != null) {
                                assertEquals(sampler.getStoneAt(x, y, z), actual,
                                        "Published Mineralogy 5.4.0 mismatch at "
                                        + seed + ":" + x + ":" + y + ":" + z);
                            }
                        }
                    }
                }
            }
        } finally {
            if (published != null) published.close();
        }

        assertEquals(SEALED_VECTOR_SHA256, hex(sealed.digest()),
                "The sealed vector digest is generated from the exact published Mineralogy 5.4.0 sampler");
        if (oracle != null) {
            assertTrue(Files.isRegularFile(oracle), "Configured Mineralogy oracle is missing: " + oracle);
        }
    }

    private static void update(MessageDigest digest, long seed, int x, int y, int z, Block block) {
        String id = ForgeRegistries.BLOCKS.getKey(block).toString();
        digest.update((seed + ":" + x + ":" + y + ":" + z + "=" + id + "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02X", value));
        return result.toString();
    }

    private static BlockState[] states(Block[] blocks) {
        BlockState[] states = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) states[i] = blocks[i].defaultBlockState();
        return states;
    }

    private static final class PublishedMineralogy implements AutoCloseable {
        private final URLClassLoader loader;
        private final Class<?> geologyClass;
        private final List<Block> igneous;
        private final List<Block> metamorphic;
        private final List<Block> sedimentary;
        private final Field thickness;
        private final List<Block> originalIgneous;
        private final List<Block> originalMetamorphic;
        private final List<Block> originalSedimentary;
        private final int originalThickness;

        @SuppressWarnings("unchecked")
        private PublishedMineralogy(URLClassLoader loader) throws Exception {
            this.loader = loader;
            geologyClass = Class.forName("com.mcmoddev.mineralogy.worldgen.Geology", true, loader);
            Class<?> registry = Class.forName("com.mcmoddev.mineralogy.init.MineralogyRegistry", true, loader);
            igneous = (List<Block>) registry.getField("igneousStones").get(null);
            metamorphic = (List<Block>) registry.getField("metamorphicStones").get(null);
            sedimentary = (List<Block>) registry.getField("sedimentaryStones").get(null);
            originalIgneous = new ArrayList<>(igneous);
            originalMetamorphic = new ArrayList<>(metamorphic);
            originalSedimentary = new ArrayList<>(sedimentary);
            Class<?> config = Class.forName("com.mcmoddev.mineralogy.MineralogyConfig", true, loader);
            thickness = config.getDeclaredField("geomLayerThickness");
            thickness.setAccessible(true);
            originalThickness = thickness.getInt(null);
        }

        static PublishedMineralogy open(Path jar) throws Exception {
            URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() },
                    LegacyMineralogyGeologyParityTest.class.getClassLoader());
            try { return new PublishedMineralogy(loader); }
            catch (Throwable failure) { loader.close(); throw failure; }
        }

        void configure(int layerThickness, Block[] igneousValues,
                Block[] metamorphicValues, Block[] sedimentaryValues) throws Exception {
            reset(igneous, igneousValues);
            reset(metamorphic, metamorphicValues);
            reset(sedimentary, sedimentaryValues);
            thickness.setInt(null, layerThickness);
        }

        PublishedSampler newSampler(long seed, double geomeSize, double layerNoise)
                throws Exception {
            Constructor<?> constructor = geologyClass.getConstructor(
                    long.class, double.class, double.class);
            Object delegate = constructor.newInstance(seed, geomeSize, layerNoise);
            return new PublishedSampler(delegate,
                    geologyClass.getMethod("getStoneAt", int.class, int.class, int.class));
        }

        @Override
        public void close() throws Exception {
            reset(igneous, originalIgneous.toArray(new Block[0]));
            reset(metamorphic, originalMetamorphic.toArray(new Block[0]));
            reset(sedimentary, originalSedimentary.toArray(new Block[0]));
            thickness.setInt(null, originalThickness);
            loader.close();
        }

        private static void reset(List<Block> target, Block[] values) {
            target.clear();
            for (Block value : values) target.add(value);
        }
    }

    private static final class PublishedSampler {
        private final Object delegate;
        private final Method getStoneAt;
        private PublishedSampler(Object delegate, Method getStoneAt) {
            this.delegate = delegate;
            this.getStoneAt = getStoneAt;
        }
        Block getStoneAt(int x, int y, int z) throws Exception {
            return (Block) getStoneAt.invoke(delegate, x, y, z);
        }
    }
}
