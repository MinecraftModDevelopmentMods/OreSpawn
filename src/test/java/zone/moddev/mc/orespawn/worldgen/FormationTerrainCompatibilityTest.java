package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.GameType;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.ChunkProviderSettings;
import net.minecraft.world.storage.WorldInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge13TestBootstrap;

class FormationTerrainCompatibilityTest {
    @BeforeAll static void bootstrap() { Forge13TestBootstrap.registerVanilla(); }
    @AfterEach void clear() { VanillaLakeCompatibility.clear(); }

    @Test void nativeWorldTypesReceiveTheSameLakeProtection() throws Exception {
        for (WorldType type : new WorldType[]{WorldType.DEFAULT, WorldType.AMPLIFIED,
                WorldType.LARGE_BIOMES, WorldType.CUSTOMIZED}) {
            ChunkProviderSettings settings = allocate(ChunkProviderSettings.class);
            ChunkProviderOverworld generator = allocate(ChunkProviderOverworld.class);
            set(ChunkProviderOverworld.class, generator, "settings", settings);
            TestWorld world = world(type, generator);
            VanillaLakeCompatibility.configure(world);
            assertSame(settings, registeredSettings().get(world), "Missing protection for " + type);
        }
    }

    @Test void customSubclassesAreNotTreatedAsTheNativeGenerator() throws Exception {
        TestWorld world = world(WorldType.DEFAULT, allocate(CustomGenerator.class));
        VanillaLakeCompatibility.configure(world);
        assertFalse(registeredSettings().containsKey(world));
        assertEquals(-1, new NaturalTerrainSurface(world).at(-950, 1033));
    }

    @Test void unrelatedGeneratorsAreRejectedWithoutSamplingThem() throws Exception {
        IChunkGenerator generator = (IChunkGenerator) Proxy.newProxyInstance(
                IChunkGenerator.class.getClassLoader(), new Class<?>[]{IChunkGenerator.class},
                (proxy, method, args) -> { throw new AssertionError("Unsupported generator was sampled"); });
        TestWorld world = world(WorldType.AMPLIFIED, generator);
        VanillaLakeCompatibility.configure(world);
        assertFalse(registeredSettings().containsKey(world));
        assertEquals(-1, new NaturalTerrainSurface(world).at(-1, -1));
    }

    @Test void replacingTheGeneratorDropsItsOldLakeSettings() throws Exception {
        ChunkProviderOverworld generator = allocate(ChunkProviderOverworld.class);
        set(ChunkProviderOverworld.class, generator, "settings", allocate(ChunkProviderSettings.class));
        TestWorld world = world(WorldType.DEFAULT, generator);
        VanillaLakeCompatibility.configure(world);
        assertTrue(registeredSettings().containsKey(world));
        set(ChunkProviderServer.class, world.chunks, "chunkGenerator", allocate(CustomGenerator.class));
        VanillaLakeCompatibility.configure(world);
        assertFalse(registeredSettings().containsKey(world));
    }

    @Test void shutdownDropsAllWorldSpecificLakeSettings() throws Exception {
        java.util.List<TestWorld> worlds = new java.util.ArrayList<>();
        for (WorldType type : new WorldType[]{WorldType.DEFAULT, WorldType.AMPLIFIED}) {
            ChunkProviderOverworld generator = allocate(ChunkProviderOverworld.class);
            set(ChunkProviderOverworld.class, generator, "settings", allocate(ChunkProviderSettings.class));
            TestWorld world = world(type, generator);
            worlds.add(world);
            VanillaLakeCompatibility.configure(world);
        }
        assertEquals(worlds.size(), registeredSettings().size());
        VanillaLakeCompatibility.clear();
        assertTrue(registeredSettings().isEmpty());
    }

    private static TestWorld world(WorldType type, IChunkGenerator generator) throws Exception {
        TestWorld world = allocate(TestWorld.class);
        world.chunks = allocate(ChunkProviderServer.class);
        set(ChunkProviderServer.class, world.chunks, "chunkGenerator", generator);
        world.info = new WorldInfo(new WorldSettings(17, GameType.SURVIVAL, true, false, type), "test");
        return world;
    }

    @SuppressWarnings("unchecked")
    private static Map<WorldServer, ChunkProviderSettings> registeredSettings() throws Exception {
        Field field = VanillaLakeCompatibility.class.getDeclaredField("SETTINGS");
        field.setAccessible(true);
        return (Map<WorldServer, ChunkProviderSettings>) field.get(null);
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field field = unsafeType.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeType.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    // These fixtures never construct a save or a native generator. Only the
    // eligibility/settings seams are used; actual generation is tested packaged.
    private static final class TestWorld extends WorldServer {
        ChunkProviderServer chunks;
        WorldInfo info;
        private TestWorld() { super(null, null, null, 0, null); }
        @Override public ChunkProviderServer getChunkProvider() { return chunks; }
        @Override public WorldInfo getWorldInfo() { return info; }
    }

    private static final class CustomGenerator extends ChunkProviderOverworld {
        private CustomGenerator() { super(null, 0, false, ""); }
    }
}
