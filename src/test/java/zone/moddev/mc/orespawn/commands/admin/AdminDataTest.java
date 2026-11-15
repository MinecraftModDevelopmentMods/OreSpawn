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
import zone.moddev.mc.orespawn.test.Forge13TestBootstrap;

class AdminDataTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { Forge13TestBootstrap.registerVanilla(); }

    @Test void exactStatesRoundTripIncludingLegacyMetadata() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.STONE, Blocks.WOOL, Blocks.WATER, Blocks.LEAVES})
            for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates())
                assertEquals(state, DiagnosticStates.resolve(DiagnosticStates.key(state)));
    }
    @Test void protectedLeafDecayChecksDoNotInvalidateAPreview() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.LEAVES, Blocks.LEAVES2})
            for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates()) {
                net.minecraft.block.state.IBlockState checked = state.withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, false);
                assertEquals(DiagnosticStates.snapshotState(state, false), DiagnosticStates.snapshotState(checked, false));
                assertEquals(DiagnosticStates.key(state), DiagnosticStates.key(DiagnosticStates.resolve(DiagnosticStates.key(state))),
                        "Reports and journals must still record exact leaf properties");
            }
    }
    @Test void clearingConfiguredLeavesStillRequiresTheirExactOriginalState() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.LEAVES, Blocks.LEAVES2}) {
            net.minecraft.block.state.IBlockState unchecked = block.getDefaultState().withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, true);
            net.minecraft.block.state.IBlockState checked = unchecked.withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, false);
            assertNotEquals(DiagnosticStates.snapshotState(unchecked, true), DiagnosticStates.snapshotState(checked, true));
        }
    }
    @Test void realLeafEditsAndEveryOtherStateStillInvalidateAPreview() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.LEAVES, Blocks.LEAVES2}) {
            net.minecraft.block.state.IBlockState original = block.getDefaultState();
            assertNotEquals(DiagnosticStates.snapshotState(original, false), DiagnosticStates.snapshotState(
                    original.cycleProperty(net.minecraft.block.BlockLeaves.DECAYABLE), false));
            assertNotEquals(DiagnosticStates.snapshotState(original, false), DiagnosticStates.snapshotState(Blocks.AIR.getDefaultState(), false));
            assertNotEquals(DiagnosticStates.snapshotState(original, false), DiagnosticStates.snapshotState(Blocks.LOG.getDefaultState(), false));
        }
        assertNotEquals(DiagnosticStates.snapshotState(Blocks.LEAVES.getStateFromMeta(0), false),
                DiagnosticStates.snapshotState(Blocks.LEAVES.getStateFromMeta(1), false));
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[]{Blocks.STONE, Blocks.WATER, Blocks.WOOL, Blocks.CHEST})
            for (net.minecraft.block.state.IBlockState state : block.getBlockState().getValidStates()) {
                assertEquals(state, DiagnosticStates.snapshotState(state, false));
                assertEquals(state, DiagnosticStates.snapshotState(state, true));
            }
    }
    @Test void chunkSamplesMeasureExactStatesWithoutPredictingTheirOrigin() {
        java.util.List<net.minecraft.util.math.BlockPos> visits = new java.util.ArrayList<>();
        com.google.gson.JsonArray samples = AdminCommands.columnSamples(position -> {
            visits.add(position);
            return Blocks.STONE.getStateFromMeta(3);
        }, -2, -3, 5, 40);
        assertEquals(4, samples.size());
        assertEquals(Arrays.asList(5, 21, 37, 40), Arrays.asList(samples.get(0).getAsJsonObject().get("y").getAsInt(),
                samples.get(1).getAsJsonObject().get("y").getAsInt(), samples.get(2).getAsJsonObject().get("y").getAsInt(),
                samples.get(3).getAsJsonObject().get("y").getAsInt()));
        assertEquals(-24, samples.get(0).getAsJsonObject().get("x").getAsInt());
        assertEquals(-40, samples.get(0).getAsJsonObject().get("z").getAsInt());
        assertEquals(DiagnosticStates.key(Blocks.STONE.getStateFromMeta(3)), samples.get(0).getAsJsonObject().get("state").getAsString());
        assertEquals(4, visits.size());
        assertEquals(new net.minecraft.util.math.BlockPos(-24, 40, -40), visits.get(3));
    }
    @Test void missingStatesNeverBecomeAir() {
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("missing:ore[]"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("minecraft:stone[variant=missing,]"));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticStates.resolve("minecraft:stone[]"));
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
        JsonObject profile = new com.google.gson.JsonParser().parse("{\"fluid_deposits\":{\"test:water\":{\"enabled\":false,\"block\":\"minecraft:water\"}},\"ores\":{\"test:stone\":{\"block\":\"minecraft:stone\",\"metadata\":3}}}").getAsJsonObject();
        DiagnosticCatalog catalog = new DiagnosticCatalog(profile);
        assertFalse(catalog.removable(Blocks.STONE.getStateFromMeta(3), false));
        assertTrue(catalog.removable(Blocks.STONE.getStateFromMeta(0), false));
        assertFalse(catalog.removable(Blocks.DIAMOND_ORE.getDefaultState(), false));
        assertFalse(catalog.removable(Blocks.FLOWING_WATER.getDefaultState(), false));
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
    @Test void protectionUsesTheGeneratorsExactIdFallbackAndMetadataClampWithoutEditingTheProfile() {
        JsonObject profile = new com.google.gson.JsonParser().parse("{\"ores\":{\"minecraft:stone\":{\"metadata\":3},\"test:wool\":{\"block\":\"minecraft:wool\",\"metadata\":999}},\"rocks\":{\"test:wool\":{\"block\":\"minecraft:wool\",\"metadata\":15}}}").getAsJsonObject();
        String before = profile.toString();
        DiagnosticCatalog catalog = new DiagnosticCatalog(profile);
        assertFalse(catalog.removable(Blocks.STONE.getStateFromMeta(3), false));
        assertFalse(catalog.removable(Blocks.WOOL.getStateFromMeta(15), false));
        assertEquals(before, profile.toString());
    }
}
