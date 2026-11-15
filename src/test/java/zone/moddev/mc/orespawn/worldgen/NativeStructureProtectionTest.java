package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.structure.StructureStart;
import net.minecraft.world.gen.feature.template.TemplateManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge28TestBootstrap;
import zone.moddev.mc.orespawn.test.TestObjects;

class NativeStructureProtectionTest {
    @BeforeAll static void bootstrap() { Forge28TestBootstrap.registerVanilla(); }

    @Test void localStartsAndNeighbourReferencesProtectTheirExactBounds() throws Exception {
        WorldGenRegion region = region();
        IChunk center = region.getChunk(-1, -1), neighbour = region.getChunk(0, -1);
        MutableBoundingBox local = new MutableBoundingBox(-16, 20, -16, -1, 40, -1);
        MutableBoundingBox crossing = new MutableBoundingBox(-8, 30, -8, 10, 50, 10);
        center.putStructureStart("Mansion", start(local));
        neighbour.putStructureStart("Mineshaft", start(crossing));
        center.addStructureReference("Mineshaft", ChunkPos.asLong(0, -1));
        List<?> boxes = boxes(region, center);
        assertEquals(2, boxes.size()); assertTrue(boxes.contains(local)); assertTrue(boxes.contains(crossing));
        Method protectedAt = NaturalFormations.class.getDeclaredMethod("protectedAt", List.class, int.class, int.class, int.class);
        protectedAt.setAccessible(true);
        assertEquals(true, protectedAt.invoke(null, boxes, -16, 20, -16));
        assertEquals(true, protectedAt.invoke(null, boxes, 10, 50, 10));
        assertEquals(false, protectedAt.invoke(null, boxes, 11, 50, 10));
    }

    @Test void anUnavailableStartFailsClosedWithoutRequestingAnotherChunk() throws Exception {
        WorldGenRegion region = region(); IChunk center = region.getChunk(-1, -1);
        center.addStructureReference("Mansion", ChunkPos.asLong(200, -200));
        assertNull(boxes(region, center));
        assertFalse(region.chunkExists(200, -200));
    }

    @Test void aMissingReferencedStartIsNotTreatedAsUnprotectedTerrain() throws Exception {
        WorldGenRegion region = region(); IChunk center = region.getChunk(-1, -1);
        center.addStructureReference("Mansion", ChunkPos.asLong(0, -1));
        assertNull(boxes(region, center));
    }

    @SuppressWarnings("unchecked")
    private static List<?> boxes(WorldGenRegion region, IChunk chunk) throws Exception {
        Method method = NaturalFormations.class.getDeclaredMethod("structures", net.minecraft.world.IWorld.class, IChunk.class);
        method.setAccessible(true); return (List<?>) method.invoke(null, region, chunk);
    }
    private static StructureStart start(MutableBoundingBox box) {
        return new StructureStart(Feature.WOODLAND_MANSION, -1, -1, Biomes.PLAINS, box, 0, 47) {
            @Override public void init(ChunkGenerator<?> generator, TemplateManager templates, int x, int z, Biome biome) { }
            @Override public boolean isValid() { return true; }
        };
    }
    private static WorldGenRegion region() throws Exception {
        WorldGenRegion region = TestObjects.allocate(WorldGenRegion.class);
        List<IChunk> chunks = new ArrayList<>();
        for (int z = -2; z <= 0; z++) for (int x = -2; x <= 0; x++)
            chunks.add(new ChunkPrimer(new ChunkPos(x, z), UpgradeData.EMPTY));
        set(region, "chunkPrimers", chunks); set(region, "field_217380_e", 3);
        set(region, "mainChunkX", -1); set(region, "mainChunkZ", -1);
        return region;
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = WorldGenRegion.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
