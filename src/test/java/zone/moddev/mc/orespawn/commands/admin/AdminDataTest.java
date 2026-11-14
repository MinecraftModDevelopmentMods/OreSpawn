package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.init.Blocks;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class AdminDataTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { Forge25TestBootstrap.registerVanilla(); }

    @Test void exactStatesRoundTripIncludingLegacyMetadata() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.STONE, Blocks.WHITE_WOOL, Blocks.WATER, Blocks.OAK_LEAVES})
            for (net.minecraft.block.state.IBlockState state : block.getStateContainer().getValidStates())
                assertEquals(state, DiagnosticStates.resolve(DiagnosticStates.key(state)));
    }
    @Test void chunkSamplesMeasureExactStatesWithoutPredictingTheirOrigin() {
        java.util.List<net.minecraft.util.math.BlockPos> visits = new java.util.ArrayList<>();
        com.google.gson.JsonArray samples = AdminCommands.columnSamples(position -> {
            visits.add(position);
            return Blocks.DIORITE.getDefaultState();
        }, -2, -3, 5, 40);
        assertEquals(4, samples.size());
        assertEquals(Arrays.asList(5, 21, 37, 40), Arrays.asList(samples.get(0).getAsJsonObject().get("y").getAsInt(),
                samples.get(1).getAsJsonObject().get("y").getAsInt(), samples.get(2).getAsJsonObject().get("y").getAsInt(),
                samples.get(3).getAsJsonObject().get("y").getAsInt()));
        assertEquals(-24, samples.get(0).getAsJsonObject().get("x").getAsInt());
        assertEquals(-40, samples.get(0).getAsJsonObject().get("z").getAsInt());
        assertEquals(DiagnosticStates.key(Blocks.DIORITE.getDefaultState()), samples.get(0).getAsJsonObject().get("state").getAsString());
        assertEquals(4, visits.size());
        assertEquals(new net.minecraft.util.math.BlockPos(-24, 40, -40), visits.get(3));
    }
    @Test void missingStatesNeverBecomeAir() {
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("missing:ore[]"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("minecraft:stone[variant=missing,]"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("minecraft:oak_log[]"));
    }
    @Test void fingerprintDoesNotDependOnObjectInsertionOrder() {
        JsonObject first = new JsonObject(), second = new JsonObject();
        first.addProperty("a", 1); first.addProperty("b", 2);
        second.addProperty("b", 2); second.addProperty("a", 1);
        assertEquals(DiagnosticFiles.fingerprint(first), DiagnosticFiles.fingerprint(second));
    }
    @Test void verifiedBackupsAreNeverOverwrittenAndDetectCorruption() throws Exception {
        Path backup = temporary.resolve("backup.json");
        JsonObject payload = new JsonObject(); payload.addProperty("saved", "state");
        DiagnosticFiles.backup(backup, payload);
        assertEquals(payload, DiagnosticFiles.readBackup(backup));
        assertThrows(java.io.IOException.class, () -> DiagnosticFiles.backup(backup, payload));
        Files.write(backup, new String(Files.readAllBytes(backup), StandardCharsets.UTF_8).replace("state", "other").getBytes(StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> DiagnosticFiles.readBackup(backup));
    }
    @Test void reportIdsCannotEscapeTheirDirectory() {
        for (String value : Arrays.asList("../world", "a/b", "a\\b", ".", "", "a:b"))
            assertThrows(IllegalArgumentException.class, () -> DiagnosticFiles.child(temporary, value));
    }
    @Test void observedCountsUseActualYBiomeAndNegativeChunkCoordinates() {
        SurveyCounts counts = new SurveyCounts();
        counts.add("ore", "ore", 3, "desert", -1, -2);
        counts.add("ore", "ore", 3, "desert", -1, -2);
        counts.add("air", "air", 4, "plains", -1, -2);
        JsonObject report = counts.json();
        assertEquals(3, report.get("sampled_volume").getAsInt());
        assertEquals(2, report.get("non_air_volume").getAsInt());
        assertEquals(2, report.getAsJsonObject("by_chunk").get("-1,-2\tore").getAsInt());
        assertEquals(2, report.getAsJsonObject("by_y").get("3\tore").getAsInt());
        assertTrue(counts.csv().get("biomes.csv").contains("\"desert\",\"ore\",2"));
    }
    @Test void cataloguePreservesVanillaOresAndConfiguredDepositsEvenWhenDisabled() {
        JsonObject profile = new com.google.gson.JsonParser().parse("{\"fluid_deposits\":{\"test:water\":{\"enabled\":false,\"block\":\"minecraft:water\"}},\"ores\":{\"test:diorite\":{\"block\":\"minecraft:diorite\"}}}").getAsJsonObject();
        DiagnosticCatalog catalog = new DiagnosticCatalog(profile);
        assertFalse(catalog.removable(Blocks.DIORITE.getDefaultState(), false));
        assertTrue(catalog.removable(Blocks.STONE.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.DIAMOND_ORE.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.WATER.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.STONE.getDefaultState(), true));
        assertFalse(catalog.removable(Blocks.CHEST.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.BEDROCK.getDefaultState(), false));
    }
    @Test void undoAllowsOnlyExpectedOrAlreadyRestoredCells() {
        EditJournal.Cell cell = new EditJournal.Cell(0, "stone", "air");
        assertTrue(EditJournal.canUndo("air", cell));
        assertTrue(EditJournal.canUndo("stone", cell));
        assertFalse(EditJournal.canUndo("player-chest", cell));
    }
    @Test void oversizedReportsStopBeforeAddingAnIncompleteCell() {
        SurveyCounts counts = new SurveyCounts(4);
        counts.add("ore", "ore", 0, "desert", 0, 0);
        assertThrows(IllegalStateException.class, () -> counts.add("air", "air", 0, "desert", 0, 0));
        assertEquals(1, counts.volume());
        assertEquals(1, counts.json().getAsJsonObject("blocks").entrySet().size());
    }
    @Test void protectionUsesExactFlattenedIdFallbackWithoutEditingTheProfile() {
        JsonObject profile = new com.google.gson.JsonParser().parse("{\"ores\":{\"minecraft:diorite\":{},\"test:wool\":{\"block\":\"minecraft:black_wool\"}},\"rocks\":{\"test:wool\":{\"block\":\"minecraft:black_wool\"}}}").getAsJsonObject();
        String before = profile.toString();
        DiagnosticCatalog catalog = new DiagnosticCatalog(profile);
        assertFalse(catalog.removable(Blocks.DIORITE.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.BLACK_WOOL.getDefaultState(), false));
        assertEquals(before, profile.toString());
    }
}
