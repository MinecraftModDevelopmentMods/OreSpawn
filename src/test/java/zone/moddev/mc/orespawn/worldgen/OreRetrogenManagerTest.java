package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import static zone.moddev.mc.orespawn.test.TestObjects.allocate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.dimension.OverworldDimension;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class OreRetrogenManagerTest {
    private WorldServer level;
    private Chunk chunk;

    @BeforeAll
    static void bootstrap() { Forge25TestBootstrap.registerVanilla(); }

    @BeforeEach
    void start() throws Exception {
        OreRetrogenManager.clear();
        level = world(0);
        chunk = chunk(level, -20, 21);
        settings(true, false, false, 777);
    }

    @AfterEach
    void stop() { OreRetrogenManager.clear(); ChunkBookkeeping.clear(); }

    @Test
    void forge25CapabilityBridgeRestoresAndPreservesCompletionWithoutTheMissingLoadEvent() throws Exception {
        NBTTagCompound disk = marker(777);
        for (int restart = 0; restart < 5; restart++) {
            OreRetrogenManager.clear(); ChunkBookkeeping.clear();
            settings(restart % 2 == 0, false, false, 777);
            level = world(0); chunk = chunk(level, -20, 21);
            net.minecraftforge.event.AttachCapabilitiesEvent<Chunk> attach =
                    new net.minecraftforge.event.AttachCapabilitiesEvent<>(Chunk.class, chunk);
            ChunkBookkeeping.attach(attach);
            net.minecraftforge.common.capabilities.ICapabilitySerializable<NBTTagCompound> storage =
                    (net.minecraftforge.common.capabilities.ICapabilitySerializable<NBTTagCompound>)
                            attach.getCapabilities().get(new net.minecraft.util.ResourceLocation("orespawn:bookkeeping"));
            storage.deserializeNBT(disk);
            ChunkBookkeeping.loaded(new net.minecraftforge.event.world.ChunkEvent.Load(chunk));
            assertEquals(0, OreRetrogenManager.queuedCount());
            for (int save = 0; save < 3; save++) {
                disk = storage.serializeNBT();
                assertEquals(777, revision(disk));
            }
            // Some Forge builds may post both events. They must not enqueue the same chunk twice.
            ChunkBookkeeping.dataLoaded(new ChunkDataEvent.Load(chunk, new NBTTagCompound()));
            assertEquals(777, revision(storage.serializeNBT()));
        }
    }

    @Test
    void completionSurvivesRepeatedSavesAndProcessRestarts() throws Exception {
        NBTTagCompound disk = marker(777);
        for (int restart = 0; restart < 5; restart++) {
            OreRetrogenManager.clear();
            settings(true, false, false, 777);
            load(chunk, disk);
            assertEquals(0, OreRetrogenManager.queuedCount());
            for (int save = 0; save < 3; save++) {
                disk = save(chunk);
                assertEquals(777, revision(disk), "A normal save must retain completed work");
            }
        }
    }

    @Test
    void disabledRetrogenStillPreservesTheSavedMarker() throws Exception {
        settings(false, false, false, 888);
        load(chunk, marker(777));
        assertEquals(777, revision(save(chunk)));
        assertEquals(0, OreRetrogenManager.queuedCount());
    }

    @Test
    void disableSaveRestartAndReenableDoesNotRepeatCompletedWork() throws Exception {
        settings(false, false, false, 777);
        load(chunk, marker(777));
        NBTTagCompound disk = save(chunk);
        OreRetrogenManager.clear();
        settings(true, false, false, 777);
        load(chunk, disk);
        assertEquals(0, OreRetrogenManager.queuedCount());
    }

    @Test
    void changingSettingsDoesNotStampAnUnprocessedRevision() throws Exception {
        load(chunk, marker(777));
        settings(true, false, false, 888);
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void anExplicitNewRevisionQueuesWithoutPretendingItHasFinished() throws Exception {
        settings(true, false, false, 888);
        load(chunk, marker(777));
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void forceStillQueuesMarkedChunksButDoesNotEraseTheirMarker() throws Exception {
        settings(true, true, false, 777);
        load(chunk, marker(777));
        load(chunk, marker(777));
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void anAbsentMarkerIsNotCompletionAtRevisionZero() throws Exception {
        settings(true, false, false, 0);
        load(chunk, new NBTTagCompound());
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertFalse(save(chunk).hasKey("OreSpawn"));
    }

    @Test
    void aStringMarkerIsNotCompletionAtRevisionZero() throws Exception {
        settings(true, false, false, 0);
        NBTTagCompound disk = new NBTTagCompound();
        NBTTagCompound root = new NBTTagCompound();
        root.setString("generation_revision", "0");
        disk.setTag("OreSpawn", root);
        load(chunk, disk);
        assertEquals(1, OreRetrogenManager.queuedCount());
    }

    @Test
    void negativeMarkersAreNotTreatedAsCompletedWork() {
        load(chunk, marker(-1));
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertFalse(save(chunk).hasKey("OreSpawn"));
    }

    @Test
    void negativeCoordinatesDoNotShareCompletion() throws Exception {
        load(chunk, marker(777));
        Chunk other = chunk(level, 21, -20);
        load(other, new NBTTagCompound());
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertFalse(save(other).hasKey("OreSpawn"));
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void differentDimensionsDoNotShareCompletion() throws Exception {
        load(chunk, marker(777));
        Chunk other = chunk(world(-1), -20, 21);
        load(other, new NBTTagCompound());
        assertFalse(save(other).hasKey("OreSpawn"));
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void separateWorldsWithTheSameDimensionDoNotShareCompletion() throws Exception {
        load(chunk, marker(777));
        Chunk other = chunk(world(0), -20, 21);
        assertFalse(save(other).hasKey("OreSpawn"));
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void reloadingTheSameChunkInOneSessionKeepsItsMarker() throws Exception {
        load(chunk, marker(777));
        NBTTagCompound disk = save(chunk);
        Chunk reloaded = chunk(level, -20, 21);
        load(reloaded, disk);
        assertEquals(0, OreRetrogenManager.queuedCount());
        assertEquals(777, revision(save(reloaded)));
    }

    @Test
    void shutdownClearsAllRememberedCompletion() {
        load(chunk, marker(777));
        OreRetrogenManager.clear();
        assertFalse(save(chunk).hasKey("OreSpawn"));
        assertEquals(0, OreRetrogenManager.queuedCount());
    }

    @Test
    void bedrockOnlyRetrogenAlsoRetainsCompletion() throws Exception {
        settings(false, false, true, 777);
        load(chunk, marker(777));
        assertEquals(0, OreRetrogenManager.queuedCount());
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void turningOffQueuedWorkDoesNotMarkItComplete() throws Exception {
        load(chunk, new NBTTagCompound());
        settings(false, false, false, 888);
        OreRetrogenManager.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        assertEquals(0, OreRetrogenManager.queuedCount());
        assertFalse(save(chunk).hasKey("OreSpawn"));
    }

    @Test
    void freshlyGeneratedChunksKeepTheRevisionTheyActuallyCompleted() throws Exception {
        markGenerated(level, chunk.getPos());
        settings(true, false, false, 888);
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void freshGenerationDoesNotMarkAnotherWorldAtTheSameCoordinates() throws Exception {
        markGenerated(level, chunk.getPos());
        assertFalse(save(chunk(world(0), -20, 21)).hasKey("OreSpawn"));
        assertEquals(777, revision(save(chunk)));
    }

    @Test
    void anUnmarkedReloadDoesNotInheritOldSessionBookkeeping() {
        load(chunk, marker(777));
        load(chunk, new NBTTagCompound());
        assertEquals(1, OreRetrogenManager.queuedCount());
        assertFalse(save(chunk).hasKey("OreSpawn"));
    }

    @Test
    void savingKeepsOtherFieldsAlreadyInTheEventData() {
        load(chunk, marker(777));
        NBTTagCompound disk = marker(1);
        disk.getCompound("OreSpawn").setString("note", "keep this");
        OreRetrogenManager.onChunkSave(new ChunkDataEvent.Save(chunk, disk));
        assertEquals(777, revision(disk));
        assertEquals("keep this", disk.getCompound("OreSpawn").getString("note"));
    }

    @Test
    void loadingDoesNotMutateTheSavedNbt() {
        NBTTagCompound disk = marker(777);
        NBTTagCompound original = disk.copy();
        load(chunk, disk);
        assertEquals(original, disk);
    }

    @Test
    void theLargestRevisionRoundTripsWithoutOverflow() throws Exception {
        settings(true, false, false, Integer.MAX_VALUE);
        load(chunk, marker(Integer.MAX_VALUE));
        assertEquals(0, OreRetrogenManager.queuedCount());
        assertEquals(Integer.MAX_VALUE, revision(save(chunk)));
    }

    private static void load(Chunk chunk, NBTTagCompound data) {
        OreRetrogenManager.onChunkLoad(new ChunkDataEvent.Load(chunk, data));
    }

    private static NBTTagCompound save(Chunk chunk) {
        NBTTagCompound data = new NBTTagCompound();
        OreRetrogenManager.onChunkSave(new ChunkDataEvent.Save(chunk, data));
        return data;
    }

    private static NBTTagCompound marker(int revision) {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound root = new NBTTagCompound();
        root.setInt("generation_revision", revision);
        data.setTag("OreSpawn", root);
        return data;
    }

    private static int revision(NBTTagCompound data) {
        assertTrue(data.getCompound("OreSpawn").contains("generation_revision", 3));
        return data.getCompound("OreSpawn").getInt("generation_revision");
    }

    private static void settings(boolean ore, boolean force, boolean bedrock, int revision) throws Exception {
        Class<?> type = Class.forName(OreRetrogenManager.class.getName() + "$Settings");
        Constructor<?> constructor = type.getDeclaredConstructor(boolean.class, boolean.class, boolean.class, int.class, int.class);
        constructor.setAccessible(true);
        Field field = OreRetrogenManager.class.getDeclaredField("settings");
        field.setAccessible(true);
        field.set(null, constructor.newInstance(ore, force, bedrock, 1, revision));
    }

    // These handlers only need chunk identity; no live server or save is opened.
    private static WorldServer world(int dimension) throws Exception {
        WorldServer level = allocate(WorldServer.class);
        set(World.class, level, "dimension", new OverworldDimension(
                dimension == -1 ? DimensionType.NETHER : DimensionType.OVERWORLD));
        return level;
    }

    private static Chunk chunk(WorldServer level, int x, int z) {
        try {
            Chunk chunk = allocate(Chunk.class);
            set(Chunk.class, chunk, "world", level);
            set(Chunk.class, chunk, "x", x);
            set(Chunk.class, chunk, "z", z);
            set(Chunk.class, chunk, "field_212816_F", new ChunkPos(x, z));
            return chunk;
        } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private static void markGenerated(WorldServer world, ChunkPos position) throws Exception {
        try {
            java.lang.reflect.Method method = OreRetrogenManager.class.getDeclaredMethod(
                    "markGenerated", WorldServer.class, ChunkPos.class);
            method.setAccessible(true);
            method.invoke(null, world, position);
        } catch (NoSuchMethodException oldSignature) {
            java.lang.reflect.Method method = OreRetrogenManager.class.getDeclaredMethod(
                    "markGenerated", net.minecraft.util.ResourceLocation.class, ChunkPos.class);
            method.setAccessible(true);
            method.invoke(null, WorldIds.dimension(world), position);
        }
    }

    private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

}


