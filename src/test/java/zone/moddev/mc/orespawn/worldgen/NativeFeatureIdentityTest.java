package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;

class NativeFeatureIdentityTest {
    @Test void managedOreFeatureKeepsItsForgeRegistryIdentity() {
        Forge31TestBootstrap.registerVanilla();
        assertEquals(new ResourceLocation("orespawn:managed_ores"), OreSpawnOreGeneration.FEATURE.getRegistryName());
    }
}
