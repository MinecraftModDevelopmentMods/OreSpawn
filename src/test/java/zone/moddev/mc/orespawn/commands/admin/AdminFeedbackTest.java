package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

class AdminFeedbackTest {
    private static final String JOB = "93612699-1d93-4b04-b988-af46c628edf0";
    private static final String TOKEN = "c1eec0f7-863f-4b6d-8a14-f509422bd1ac";

    @Test void previewSeparatesLocationCountsWarningAndActionsWithoutShowingIds() {
        List<ITextComponent> lines = preview(false);
        assertEquals(6, lines.size());
        assertEquals("commands.orespawn.preview_title", translated(lines.get(0)).getKey());
        Object[] location = translated(lines.get(1)).getFormatArgs();
        assertEquals("value.orespawn.dimension.overworld", translated((ITextComponent) location[0]).getKey());
        assertArrayEquals(new Object[]{-18, 3, 0, 255}, java.util.Arrays.copyOfRange(location, 1, location.length));
        assertEquals("commands.orespawn.preview_clear_counts", translated(lines.get(2)).getKey());
        assertArrayEquals(new Object[]{14468, 51068}, translated(lines.get(2)).getFormatArgs());
        assertEquals("commands.orespawn.preview_warning", translated(lines.get(3)).getKey());
        assertEquals("commands.orespawn.preview_expiry", translated(lines.get(4)).getKey());
        for (ITextComponent line : lines) {
            assertFalse(line.getString().contains(JOB));
            assertFalse(line.getString().contains(TOKEN));
        }
    }

    @Test void clearActionsCarryTheExactConfirmationCancelAndDetailsCommands() {
        List<ITextComponent> actions = preview(false).get(5).getSiblings();
        assertEquals(5, actions.size());
        assertAction(actions.get(0), "confirm_clear", "/orespawn clearchunk confirm " + TOKEN);
        assertAction(actions.get(2), "preview_cancel", "/orespawn clearchunk cancel " + TOKEN);
        assertAction(actions.get(4), "details", "/orespawn jobs " + JOB);
        assertEquals(TextFormatting.RED, actions.get(0).getStyle().getColor());
        assertTrue(actions.get(0).getStyle().getBold());
        assertTrue(actions.get(0).getStyle().getUnderlined());
        assertEquals(HoverEvent.Action.SHOW_TEXT, actions.get(0).getStyle().getHoverEvent().getAction());
    }

    @Test void undoHasItsOwnWarningAndBackupBoundConfirmation() {
        List<ITextComponent> lines = preview(true);
        assertEquals("commands.orespawn.preview_undo_counts", translated(lines.get(2)).getKey());
        assertEquals("commands.orespawn.preview_undo_warning", translated(lines.get(3)).getKey());
        assertAction(lines.get(5).getSiblings().get(0), "confirm_undo", "/orespawn undo backup-id confirm " + TOKEN);
        assertAction(lines.get(5).getSiblings().get(2), "preview_cancel", "/orespawn clearchunk cancel " + TOKEN);
    }

    @Test void packetRoundTripKeepsClickableActions() {
        ITextComponent copy = ITextComponent.Serializer.fromJson(ITextComponent.Serializer.toJson(preview(false).get(5)));
        assertNotNull(copy);
        assertAction(copy.getSiblings().get(0), "confirm_clear", "/orespawn clearchunk confirm " + TOKEN);
        assertAction(copy.getSiblings().get(2), "preview_cancel", "/orespawn clearchunk cancel " + TOKEN);
    }

    @Test void consolesStillReceiveACopyableCommandWithoutWorldAccess() {
        Sender sender = new Sender();
        AdminFeedback.preview(sender, JOB, TOKEN, false, null, "example:moon", -18, 3, 0, 255, 14468, 51068);
        assertEquals(7, sender.messages.size());
        assertEquals("/orespawn clearchunk confirm " + TOKEN, sender.messages.get(6).getString());
        assertEquals("example:moon", ((ITextComponent) translated(sender.messages.get(1)).getFormatArgs()[0]).getString());
    }

    @Test void completionOffersAnUndoPreviewInTheOriginalDimensionNotAnImmediateRestore() {
        Sender sender = new Sender();
        AdminFeedback.completed(sender, JOB, 14468, false, "backup-id", "minecraft:the_nether");
        TextComponentTranslation completed = translated(sender.messages.get(0));
        assertEquals("commands.orespawn.complete_clear", completed.getKey());
        assertEquals(14468, completed.getFormatArgs()[0]);
        assertAction((ITextComponent) completed.getFormatArgs()[1], "undo_preview", "/orespawn undo backup-id --dimension minecraft:the_nether");
        assertEquals("/orespawn undo backup-id --dimension minecraft:the_nether", sender.messages.get(1).getString());
    }

    @Test void completedUndoDoesNotOfferAnotherUndoOrShowTheBackupId() {
        Sender sender = new Sender();
        AdminFeedback.completed(sender, JOB, 14468, true, "backup-id", "minecraft:overworld");
        assertEquals(1, sender.messages.size());
        assertEquals("commands.orespawn.complete_undo", translated(sender.messages.get(0)).getKey());
        assertFalse(sender.messages.get(0).getString().contains("backup-id"));
    }

    @Test void routineJobsUseADetailsLinkAndKeepLargeFailureTextBounded() {
        Sender sender = new Sender();
        AdminFeedback.started(sender, JOB);
        assertAction((ITextComponent) translated(sender.messages.get(0)).getFormatArgs()[0], "details", "/orespawn jobs " + JOB);
        AdminFeedback.result(sender, JOB, "failed", String.join("", java.util.Collections.nCopies(40000, "x")));
        TextComponentTranslation result = translated(sender.messages.get(2));
        String message = (String) result.getFormatArgs()[2];
        assertEquals(1024, message.length());
        assertTrue(message.endsWith("..."));
    }

    @Test void dismissingAnUnknownPreviewNeedsNoWorldAndCannotBypassPermissions() {
        Sender sender = new Sender();
        assertFalse(AdminJobs.cancelPreview(sender, TOKEN));
        assertThrows(IllegalArgumentException.class, () -> AdminJobs.cancelPreview(new Sender() {
            @Override public boolean canUseCommand(int level, String command) { return level <= 2; }
        }, TOKEN));
    }

    private static List<ITextComponent> preview(boolean undo) {
        return AdminFeedback.previewLines(JOB, TOKEN, undo, "backup-id", "minecraft:overworld", -18, 3, 0, 255, 14468, 51068);
    }

    private static TextComponentTranslation translated(ITextComponent component) {
        assertTrue(component instanceof TextComponentTranslation);
        return (TextComponentTranslation) component;
    }

    private static void assertAction(ITextComponent component, String key, String command) {
        assertEquals("commands.orespawn." + key, translated(component).getKey());
        assertNotNull(component.getStyle().getClickEvent());
        assertEquals(ClickEvent.Action.RUN_COMMAND, component.getStyle().getClickEvent().getAction());
        assertEquals(command, component.getStyle().getClickEvent().getValue());
    }

    private static class Sender implements AdminSource {
        final List<ITextComponent> messages = new ArrayList<>();
        public String getName() { return "test"; }
        public ITextComponent getDisplayName() { return new TextComponentString("test"); }
        public void sendMessage(ITextComponent text) { messages.add(text); }
        public boolean canUseCommand(int level, String command) { return true; }
        public BlockPos getPosition() { throw new AssertionError("Unexpected world access"); }
        public Vec3d getPositionVector() { throw new AssertionError("Unexpected world access"); }
        public net.minecraft.world.WorldServer getEntityWorld() { throw new AssertionError("Unexpected world access"); }
        public Entity getCommandSenderEntity() { return null; }
        public boolean sendCommandFeedback() { return true; }
        public MinecraftServer getServer() { throw new AssertionError("Unexpected server access"); }
    }
}
