package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.api.OreSpawnApi;

class NativeStonePatchContextTest {
    @AfterEach void reset() { NativeStonePatchContext.clear(); }
    @Test void onlyThreeNativeStonePatchesAreMarked() {
        for (EventType type : EventType.values()) {
            NativeStonePatchContext.select(true, type);
            assertEquals(type == EventType.GRANITE || type == EventType.DIORITE || type == EventType.ANDESITE,
                    OreSpawnApi.isNativeStonePatchPass(), type.toString());
        }
    }
    @Test void absentFormationsAndPostResetRetainOrdinaryReplacement() {
        NativeStonePatchContext.select(false, EventType.GRANITE);
        assertFalse(OreSpawnApi.isNativeStonePatchPass());
        NativeStonePatchContext.select(true, EventType.GRANITE);
        assertTrue(OreSpawnApi.isNativeStonePatchPass());
        NativeStonePatchContext.reset();
        assertFalse(OreSpawnApi.isNativeStonePatchPass());
    }
    @Test void passContextDoesNotLeakAcrossThreads() throws Exception {
        NativeStonePatchContext.select(true, EventType.ANDESITE);
        final boolean[] observed = {true};
        Thread other = new Thread(() -> observed[0] = OreSpawnApi.isNativeStonePatchPass());
        other.start(); other.join();
        assertFalse(observed[0]);
        assertTrue(OreSpawnApi.isNativeStonePatchPass());
    }
    @Test void nestedOrePassRestoresOuterNativePatchAndWorldClearDropsFrames() {
        NativeStonePatchContext.select(true, EventType.GRANITE);
        NativeStonePatchContext.begin();
        assertFalse(OreSpawnApi.isNativeStonePatchPass());
        NativeStonePatchContext.select(true, EventType.IRON);
        NativeStonePatchContext.end();
        assertTrue(OreSpawnApi.isNativeStonePatchPass());
        NativeStonePatchContext.begin();
        NativeStonePatchContext.clear();
        NativeStonePatchContext.end();
        assertFalse(OreSpawnApi.isNativeStonePatchPass());
    }
    @Test void releasedTerrainCacheOwnerCannotLoadAnyChunk() throws Exception {
        NaturalTerrainSurface surface = new NaturalTerrainSurface(null);
        java.lang.reflect.Field owner = NaturalTerrainSurface.class.getDeclaredField("world");
        assertEquals(java.lang.ref.WeakReference.class, owner.getType());
        assertEquals(-1, surface.at(-1,-1));
    }
}
