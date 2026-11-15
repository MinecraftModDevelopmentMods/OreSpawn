package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.MapGenStructureData;
import net.minecraft.world.storage.MapStorage;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.TestObjects;

class NaturalFormationStorageTest {
    @Test void readsLiveNativeStartsFromTheDimensionStoreWithoutConsultingSharedData() throws Exception {
        StoreWorld world = world("Mineshaft", new int[]{-16, 15, 32, -1, 40, 47});
        assertArrayEquals(new int[]{-16, 15, 32, -1, 40, 47}, boxes(world, -1, 2).get(0));
        assertTrue(boxes(world, 5, 2).isEmpty());
    }

    @Test void woodlandMansionsHaveTheirNativeProtectionToo() throws Exception {
        StoreWorld world = world("Mansion", new int[]{16, 15, -32, 47, 80, -1});
        assertEquals(1, boxes(world, 1, -1).size());
    }

    @Test void matchingStructureNamesInDifferentDimensionsDoNotShareBoxes() throws Exception {
        StoreWorld first = world("Mineshaft", new int[]{-16, 15, 32, -1, 40, 47});
        StoreWorld second = world("Mineshaft", new int[]{160, 15, 32, 175, 40, 47});
        assertEquals(1, boxes(first, -1, 2).size());
        assertTrue(boxes(second, -1, 2).isEmpty());
        assertEquals(1, boxes(second, 10, 2).size());
    }

    private static StoreWorld world(String name, int[] box) throws Exception {
        StoreWorld world = TestObjects.allocate(StoreWorld.class);
        world.storage = new MapStorage(null);
        MapGenStructureData data = new MapGenStructureData(name);
        NBTTagCompound start = new NBTTagCompound(); start.setIntArray("BB", box);
        data.getTagCompound().setTag("fixture", start);
        world.storage.setData(name, data);
        return world;
    }

    @SuppressWarnings("unchecked")
    private static List<int[]> boxes(WorldServer world, int x, int z) throws Exception {
        Method method = NaturalFormations.class.getDeclaredMethod("structures", WorldServer.class, int.class, int.class);
        method.setAccessible(true);
        return (List<int[]>) method.invoke(null, world, x, z);
    }

    private static final class StoreWorld extends WorldServer {
        MapStorage storage;
        private StoreWorld() { super(null, null, null, 0, null); }
        @Override public MapStorage getPerWorldStorage() { return storage; }
        @Override public MapStorage getMapStorage() { throw new AssertionError("Native structures do not use the shared store"); }
    }
}
