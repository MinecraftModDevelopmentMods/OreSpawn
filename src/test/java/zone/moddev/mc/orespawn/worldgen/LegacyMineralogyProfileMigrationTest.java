package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.registry.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

class LegacyMineralogyProfileMigrationTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void carriedMineralogy110ConfigRetainsItsExactCyanoContract(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "FML", "ModList", "3.3.8.26");
        Path config = config(root, "mineralogy.cfg",
                "I:GEOME_SIZE=144\n"
                + "B:REALISTIC_COAL_LAYERS=true\n"
                + "S:ROCK_LAYER_NOISE=41.5\n"
                + "I:ROCK_LAYER_THICKNESS=11\n"
                + "S:igneous_whitelist=minecraft:obsidian;mineralogy:diabase\n"
                + "S:igneous_blacklist=mineralogy:gabbro\n"
                + "S:metamorphic_whitelist=minecraft:cobblestone\n"
                + "S:metamorphic_blacklist=mineralogy:slate\n"
                + "S:sedimentary_whitelist=minecraft:gravel\n"
                + "S:sedimentary_blacklist=mineralogy:gypsum\n");
        String sourceHash = sha256(config.resolve("mineralogy.cfg"));

        WorldGeologyProfile migrated = migrate(world, config);

        assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
        assertTrue(migrated.cyanoEnabled());
        assertTrue(migrated.cyanoRealisticCoalLayers());
        assertEquals(144, migrated.cyanoGeomeSize());
        assertEquals(41.5D, migrated.cyanoRockLayerNoise());
        assertEquals(11, migrated.cyanoLayerThickness());
        JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");
        assertEquals("Mineralogy 1.10", string(cyano, "legacy_lineage"));
        assertEquals(Arrays.asList("minecraft:obsidian", "mineralogy:diabase"),
                strings(cyano, "igneous_whitelist"));
        assertFalse(strings(cyano, "igneous_rocks").contains("mineralogy:gabbro"));
        assertTrue(strings(cyano, "sedimentary_rocks").contains("minecraft:coal_ore"));
        assertEquals(sourceHash, sha256(config.resolve("mineralogy.cfg")));
        assertReport(world, "Selected config lineage: Mineralogy 1.10",
                "Selected engine: LEGACY", "Cyano layer engine");
    }

    @Test
    void nativeMineralogy112RetainsDuplicateRockSaltAndDisabledState(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "FML", "ModList", "3.8.0.53");
        Path config = config(root, "mineralogy.cfg",
                "B:PLACE_MINERALOGY_ROCK=false\n"
                + "I:GEOME_SIZE=128\n"
                + "S:ROCK_LAYER_NOISE=37.25\n"
                + "I:ROCK_LAYER_THICKNESS=9\n");

        WorldGeologyProfile migrated = migrate(world, config);
        JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

        assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
        assertFalse(migrated.cyanoEnabled());
        assertEquals("Mineralogy 1.12", string(cyano, "legacy_lineage"));
        assertEquals(2, count(strings(cyano, "sedimentary_rocks"), "mineralogy:rock_salt"));
        assertReport(world, "Legacy Mineralogy geology was disabled and remains disabled",
                "Source file found: yes");
    }

    @Test
    void publishedMineralogy501TomlPreservesEngineNumbersAndAllLists(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.0.1");
        Path config = config(root, "mineralogy-common.toml",
                "[options]\n"
                + "PLACE_MINERALOGY_ROCK = true\n"
                + "[world-gen]\n"
                + "GEOLOGY_MODE = \"GEOME\"\n"
                + "GEOME_SIZE = 196\n"
                + "ROCK_LAYER_NOISE = 44.75\n"
                + "ROCK_LAYER_THICKNESS = 13\n"
                + "igneous_whitelist = [\"minecraft:obsidian\", \"minecraft:obsidian\", \"minecraft:netherrack\"]\n"
                + "igneous_blacklist = [\"mineralogy:basalt\"]\n"
                + "metamorphic_whitelist = [\"minecraft:cobblestone\"]\n"
                + "metamorphic_blacklist = [\"mineralogy:slate\"]\n"
                + "sedimentary_whitelist = [\n"
                + "  \"minecraft:gravel\", # retained comment\n"
                + "  \"minecraft:sand\"\n"
                + "]\n"
                + "sedimentary_blacklist = [\"mineralogy:gypsum\"]\n");

        WorldGeologyProfile migrated = migrate(world, config);
        JsonObject cyano = migrated.toJson().getAsJsonObject("cyano");

        assertEquals(GeologyMode.GEOME, migrated.geologyMode());
        assertTrue(migrated.cyanoEnabled());
        assertEquals(196, migrated.cyanoGeomeSize());
        assertEquals(44.75D, migrated.cyanoRockLayerNoise());
        assertEquals(13, migrated.cyanoLayerThickness());
        assertEquals("Mineralogy 5.x", string(cyano, "legacy_lineage"));
        assertEquals(Arrays.asList("minecraft:obsidian", "minecraft:obsidian", "minecraft:netherrack"),
                strings(cyano, "igneous_whitelist"));
        assertEquals(1, count(strings(cyano, "igneous_rocks"), "minecraft:obsidian"),
                "Mineralogy 5 deduplicated whitelist additions before sampling");
        assertFalse(strings(cyano, "igneous_rocks").contains("mineralogy:basalt"));
        assertEquals(Arrays.asList("minecraft:gravel", "minecraft:sand"),
                strings(cyano, "sedimentary_whitelist"));
        assertReport(world, "Saved mod metadata: level.dat (fml/LoadingModList)",
                "Selected engine: GEOME", "Mineralogy geome engine");
    }

    @Test
    void mineralogy5LegacyEngineChoiceRemainsCyano(@TempDir Path root) throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.3.0");
        Path config = config(root, "mineralogy-common.toml",
                "[options]\nPLACE_MINERALOGY_ROCK = true\n"
                + "[world-gen]\nGEOLOGY_MODE = \"LEGACY\"\n");

        WorldGeologyProfile migrated = migrate(world, config);

        assertEquals(GeologyMode.LEGACY, migrated.geologyMode());
        assertReport(world, "Selected engine: LEGACY", "Cyano layer engine");
    }

    @Test
    void malformedMineralogy5ValuesUsePublishedDefaultsWithoutBroadening(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.0.1");
        Path config = config(root, "mineralogy-common.toml",
                "[world-gen]\nGEOLOGY_MODE = \"unknown\"\nGEOME_SIZE = \"bad\"\n"
                + "ROCK_LAYER_NOISE = -2\nROCK_LAYER_THICKNESS = 9999\n");

        WorldGeologyProfile migrated = migrate(world, config);

        assertEquals(GeologyMode.GEOME, migrated.geologyMode());
        assertEquals(100, migrated.cyanoGeomeSize());
        assertEquals(1.0D, migrated.cyanoRockLayerNoise());
        assertEquals(255, migrated.cyanoLayerThickness());
        assertReport(world, "Invalid GEOLOGY_MODE 'unknown'", "published GEOME default used");
    }

    @Test
    void savedLineageWinsWhenAnotherStaleConfigFileIsPresent(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.0.1");
        Path config = config(root, "mineralogy.cfg",
                "B:PLACE_MINERALOGY_ROCK=false\nI:GEOME_SIZE=144\n");

        WorldGeologyProfile migrated = migrate(world, config);

        assertTrue(migrated.cyanoEnabled());
        assertEquals(100, migrated.cyanoGeomeSize());
        assertReport(world, "Source file found: no; published Mineralogy 5.x defaults used",
                "Found mineralogy.cfg but saved world metadata selects Mineralogy 5.x");
    }

    @Test
    void freshWorldWithStaleLegacyFilesKeepsCurrentCreateWorldChoice(@TempDir Path root)
            throws Exception {
        Path fresh = root.resolve("fresh");
        Files.createDirectories(fresh);
        writeLevelDat(fresh, "fml", "LoadingModList", "5.0.1");
        Path config = config(root, "mineralogy-common.toml",
                "[world-gen]\nGEOLOGY_MODE=\"LEGACY\"\n");

        assertNull(migrate(fresh, config));
    }

    @Test
    void validLevelDatOldIsUsedWhenCurrentMetadataCannotBeRead(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.0.1");
        Files.move(world.resolve("level.dat"), world.resolve("level.dat_old"));
        Files.write(world.resolve("level.dat"), new byte[] { 1, 2, 3, 4 });
        Path config = config(root, "mineralogy-common.toml", "[world-gen]\nGEOME_SIZE=121\n");

        WorldGeologyProfile migrated = migrate(world, config);

        assertEquals(121, migrated.cyanoGeomeSize());
        assertReport(world, "Saved mod metadata: level.dat_old (fml/LoadingModList)");
    }

    @Test
    void modernMineralogyWorldIsNotReclassifiedByOldFiles(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "6.0.0");
        Path config = config(root, "mineralogy.cfg", "I:GEOME_SIZE=144\n");
        assertNull(migrate(world, config));
    }

    @Test
    void migrationAndReportAreByteStableAndSourceRemainsUntouched(@TempDir Path root)
            throws Exception {
        Path world = existingWorld(root, "fml", "LoadingModList", "5.0.1");
        Path config = config(root, "mineralogy-common.toml",
                "[world-gen]\nGEOLOGY_MODE=\"LEGACY\"\nGEOME_SIZE=111\n");
        Path source = config.resolve("mineralogy-common.toml");
        String sourceHash = sha256(source);

        WorldGeologyProfile first = migrate(world, config);
        Path report = world.resolve("serverconfig/orespawn-upgrade-report.txt");
        byte[] reportBytes = Files.readAllBytes(report);
        WorldGeologyProfile second = migrate(world, config);

        assertEquals(first.toJson(), second.toJson());
        assertTrue(Arrays.equals(reportBytes, Files.readAllBytes(report)));
        assertEquals(sourceHash, sha256(source));
    }

    @Test
    void snapshottedOrderPreservesDuplicatesAndFallsBackOnlyForEmptyFamily() {
        WorldGeologyProfile base = WorldGeologyProfile.recommended(false);
        JsonObject root = base.rootCopy();
        JsonObject cyano = new JsonObject();
        cyano.add("sedimentary_rocks", array(
                "minecraft:sandstone", "minecraft:coal_ore", "minecraft:sandstone"));
        cyano.addProperty("migrated_from", "test");
        root.add("cyano", cyano);
        BlockState[] resolved = Geology.resolveRockOrder(base.withRoot(root),
                "sedimentary_rocks", new BlockState[] { Blocks.BEDROCK.getDefaultState() });
        assertEquals(3, resolved.length);
        assertEquals(Blocks.SANDSTONE, resolved[0].getBlock());
        assertEquals(Blocks.COAL_ORE, resolved[1].getBlock());
        assertEquals(Blocks.SANDSTONE, resolved[2].getBlock());

        JsonObject missingRoot = base.rootCopy();
        JsonObject missingCyano = new JsonObject();
        missingCyano.add("igneous_rocks", array("missingmod:removed_rock"));
        missingCyano.addProperty("migrated_from", "test");
        missingRoot.add("cyano", missingCyano);
        BlockState[] fallback = { Blocks.OBSIDIAN.getDefaultState() };
        assertEquals(Blocks.OBSIDIAN, Geology.resolveRockOrder(base.withRoot(missingRoot),
                "igneous_rocks", fallback)[0].getBlock());
    }

    private static WorldGeologyProfile migrate(Path world, Path config) {
        return LegacyMineralogyProfileMigration.migrateIfNeeded(
                world, config, WorldGeologyProfile.recommended(false));
    }

    private static Path existingWorld(Path root, String compound, String list,
            String version) throws IOException {
        Path world = root.resolve("world");
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"), new byte[] { 0 });
        writeLevelDat(world, compound, list, version);
        return world;
    }

    private static Path config(Path root, String name, String contents) throws IOException {
        Path config = root.resolve("config");
        Files.createDirectories(config);
        Files.write(config.resolve(name), contents.getBytes(StandardCharsets.UTF_8));
        return config;
    }

    private static void writeLevelDat(Path world, String compound, String list,
            String version) throws IOException {
        CompoundNBT root = new CompoundNBT();
        CompoundNBT fml = new CompoundNBT();
        ListNBT mods = new ListNBT();
        CompoundNBT mod = new CompoundNBT();
        mod.putString("ModId", "mineralogy");
        mod.putString("ModVersion", version);
        mods.add(mod);
        fml.put(list, mods);
        root.put(compound, fml);
        try (FileOutputStream output = new FileOutputStream(world.resolve("level.dat").toFile())) {
            CompressedStreamTools.writeCompressed(root, output);
        }
    }

    private static void assertReport(Path world, String... fragments) throws IOException {
        String report = new String(Files.readAllBytes(
                world.resolve("serverconfig/orespawn-upgrade-report.txt")), StandardCharsets.UTF_8);
        for (String fragment : fragments) assertTrue(report.contains(fragment), fragment);
    }

    private static String string(JsonObject parent, String key) {
        return parent.get(key).getAsString();
    }

    private static List<String> strings(JsonObject parent, String key) {
        List<String> result = new ArrayList<>();
        for (JsonElement value : parent.getAsJsonArray(key)) result.add(value.getAsString());
        return result;
    }

    private static int count(List<String> values, String expected) {
        int count = 0;
        for (String value : values) if (expected.equals(value)) count++;
        return count;
    }

    private static JsonArray array(String... values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(new JsonPrimitive(value));
        return result;
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] result = digest.digest(Files.readAllBytes(path));
        StringBuilder hex = new StringBuilder();
        for (byte value : result) hex.append(String.format("%02X", value));
        return hex.toString();
    }
}
