package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

class AdminSafetyTest {
    @Test void confirmationExpiresAtExactlyTwoMinutes() {
        PreviewLease lease = new PreviewLease("operator", "profile", 100);
        assertTrue(lease.accepts("operator", "profile", 100));
        assertTrue(lease.accepts("operator", "profile", 100 + PreviewLease.LIFETIME - 1));
        assertFalse(lease.accepts("operator", "profile", 100 + PreviewLease.LIFETIME));
        assertFalse(lease.accepts("operator", "profile", 99));
    }
    @Test void confirmationRequiresOriginalOperatorAndProfile() {
        PreviewLease lease = new PreviewLease("alice", "original", 0);
        assertFalse(lease.accepts("bob", "original", 1));
        assertFalse(lease.accepts("alice", "edited", 1));
    }
    @Test void destructiveCommandsRejectLevelTwoBeforeAccessingAnyWorld() {
        for (String[] command : new String[][]{{"clearchunk"}, {"clearchunk", "confirm", "fake"}, {"clearchunk", "cancel", "fake"},
                {"undo", "backup"}, {"undo", "backup", "confirm", "fake"}})
            assertThrows(AdminFailure.class, () -> AdminCommands.handle(null, new Sender(2), command));
    }
    @Test void diagnosticCommandsRejectNonOperatorsBeforeAccessingAnyWorld() {
        for (String action : Arrays.asList("help", "chunk", "survey", "explain", "jobs", "cancel"))
            assertThrows(AdminFailure.class, () -> AdminCommands.handle(null, new Sender(0), new String[]{action}));
    }
    @Test void ordinaryStatusAndExistingMaintenanceStillUseTheirOriginalHandler() throws Exception {
        for (String action : Arrays.asList("status", "reload", "retrogen", "dump-biomes"))
            assertFalse(AdminCommands.handle(null, new Sender(0), new String[]{action}));
    }
    @Test void helpRequiresNoWorldAndUsesTranslatedMessages() throws Exception {
        Sender sender = new Sender(2);
        assertTrue(AdminCommands.handle(null, sender, new String[]{"help"}));
        assertEquals(4, sender.messages);
    }
    @Test void radiusAndCoordinateLimitsRejectOverflowAndMalformedNumbers() {
        for (String value : Arrays.asList("-1", "9", "999999999999999", "oops"))
            assertThrows(IllegalArgumentException.class, () -> AdminCommands.integer(value, 0, 8));
        assertEquals(8, AdminCommands.integer("8", 0, 8));
        assertEquals(-2, AdminCommands.integer("-2", -1874999, 1874999));
    }
    @Test void journalsRejectDuplicateOutOfChunkAndUnchangedCells() {
        JsonObject header = header();
        EditJournal.Cell cell = new EditJournal.Cell(0, "stone", "air");
        assertThrows(IllegalArgumentException.class, () -> new EditJournal(header, Arrays.asList(cell, cell)));
        assertThrows(IllegalArgumentException.class, () -> new EditJournal(header, Collections.singletonList(new EditJournal.Cell(65536, "stone", "air"))));
        assertThrows(IllegalArgumentException.class, () -> new EditJournal(header, Collections.singletonList(new EditJournal.Cell(0, "air", "air"))));
    }
    @Test void journalsRoundTripAndDefensivelyCopyTheirHeader() {
        JsonObject header = header();
        EditJournal journal = new EditJournal(header, Collections.singletonList(new EditJournal.Cell(123, "stone", "air")));
        header.addProperty("world", "other");
        EditJournal copy = EditJournal.read(journal.json());
        assertEquals("test", copy.header.get("world").getAsString());
        assertEquals(123, copy.cells.get(0).index);
        assertEquals("stone", copy.cells.get(0).before);
        assertThrows(UnsupportedOperationException.class, () -> copy.cells.clear());
    }
    private static JsonObject header() {
        JsonObject result = new JsonObject();
        result.addProperty("minecraft_version", "1.13.2");
        for (String key : new String[]{"world", "dimension", "chunk_x", "chunk_z", "profile", "operator", "snapshot"}) result.addProperty(key, "test");
        return result;
    }
    @Test void anotherMinecraftVersionsCoordinateEncodingCannotBeRestored() {
        JsonObject header = header(); header.addProperty("minecraft_version", "1.21.1");
        assertThrows(IllegalArgumentException.class, () -> new EditJournal(header, Collections.singletonList(new EditJournal.Cell(0, "stone", "air"))));
    }
    @Test void fileBacklogIsBoundedAndNeverRunsOverflowWorkOnTheServerThread() throws Exception {
        java.util.concurrent.ExecutorService worker = AdminJobs.newFileWorker();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        try {
            worker.submit(() -> { started.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS));
            for (int i = 0; i < 32; i++) worker.submit(() -> { });
            assertThrows(java.util.concurrent.RejectedExecutionException.class, () -> worker.submit(() -> { fail("Overflow must not run inline"); }));
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void largeJobReportsCannotOverflowMinecraftChatPackets() throws Exception {
        java.lang.reflect.Field field = AdminJobs.class.getDeclaredField("HISTORY");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, JsonObject> history = (java.util.Map<String, JsonObject>) field.get(null);
        String id = "large-chat-regression";
        JsonObject report = new JsonObject();
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 40000; i++) large.append('地').append('"').append((char) 92);
        report.addProperty("message", large.toString());
        history.put(id, report);
        try {
            Sender sender = new Sender(2);
            assertTrue(AdminCommands.handle(null, sender, new String[]{"jobs", id}));
            String packet = ITextComponent.Serializer.toJson(sender.lastMessage);
            assertTrue(packet.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32767,
                    "Diagnostic feedback must fit a chat packet; the complete report remains on disk");
            assertEquals(large.toString(), history.get(id).get("message").getAsString());
        } finally { history.remove(id); }
    }
    private static final class Sender implements AdminSource {
        final int level;
        int messages;
        ITextComponent lastMessage;
        Sender(int level) { this.level = level; }
        public String getName() { return "test"; }
        public ITextComponent getDisplayName() { return new TextComponentString("test"); }
        public void sendMessage(ITextComponent text) { messages++; lastMessage = text; }
        public boolean canUseCommand(int required, String name) { return level >= required; }
        public BlockPos getPosition() { throw new AssertionError("Unexpected world access"); }
        public Vec3d getPositionVector() { throw new AssertionError("Unexpected world access"); }
        public net.minecraft.world.WorldServer getEntityWorld() { throw new AssertionError("Unexpected world access"); }
        public Entity getCommandSenderEntity() { return null; }
        public boolean sendCommandFeedback() { return true; }
        public MinecraftServer getServer() { return null; }
    }
}
