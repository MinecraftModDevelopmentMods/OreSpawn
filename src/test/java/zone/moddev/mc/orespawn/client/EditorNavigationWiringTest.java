package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/** Keeps the main routes connected when a screen is moved between versions. */
class EditorNavigationWiringTest {
    @Test
    void oreSourcesAndModsHaveVisibleEntryPoints() throws Exception {
        assertTrue(classBytes(GeologyMaterialsScreen.class).contains("OreSourceListScreen"),
                "The ORES tab must open the Ore Sources editor");

        Class<?> entry;
        try {
            entry = Class.forName("zone.moddev.mc.orespawn.client.OreSpawnWorldCreationTab");
        } catch (ClassNotFoundException olderTarget) {
            entry = OreSpawnWorldSettingsScreen.class;
        }
        assertTrue(classBytes(entry).contains("OreSpawnModsScreen"),
                "The world editor must open the Mods directory");
    }

    private static String classBytes(Class<?> type) throws IOException {
        try (InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            if (stream == null) throw new IOException("Compiled class is missing: " + type.getName());
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
