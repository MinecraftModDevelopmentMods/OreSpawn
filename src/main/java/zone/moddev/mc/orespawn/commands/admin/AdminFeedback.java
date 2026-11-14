package zone.moddev.mc.orespawn.commands.admin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

/** Keeps routine chat short while leaving exact commands and reports available. */
public final class AdminFeedback {
    private AdminFeedback() { }

    static ITextComponent action(String key, String command, TextFormatting colour) {
        ITextComponent text = new TextComponentTranslation("commands.orespawn." + key);
        text.getStyle().setColor(colour).setBold(true).setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentTranslation("commands.orespawn.action_hint", command)));
        return text;
    }

    static String confirmation(String token, boolean undo, String backupId) {
        return undo ? "/orespawn undo " + backupId + " confirm " + token : "/orespawn clearchunk confirm " + token;
    }

    static ITextComponent details(String id) {
        return action("details", "/orespawn jobs " + id, TextFormatting.AQUA);
    }

    static List<ITextComponent> previewLines(String id, String token, boolean undo, String backupId,
            String dimension, int cx, int cz, int minY, int maxY, int changes, int preserved) {
        List<ITextComponent> lines = new ArrayList<>();
        ITextComponent title = new TextComponentTranslation("commands.orespawn.preview_title");
        title.getStyle().setColor(TextFormatting.YELLOW).setBold(true);
        lines.add(title);
        lines.add(new TextComponentTranslation("commands.orespawn.preview_location", dimensionLabel(dimension), cx, cz, minY, maxY));
        lines.add(new TextComponentTranslation("commands.orespawn." + (undo ? "preview_undo_counts" : "preview_clear_counts"), changes, preserved));
        ITextComponent warning = new TextComponentTranslation("commands.orespawn." + (undo ? "preview_undo_warning" : "preview_warning"));
        warning.getStyle().setColor(TextFormatting.YELLOW);
        lines.add(warning);
        ITextComponent expiry = new TextComponentTranslation("commands.orespawn.preview_expiry");
        expiry.getStyle().setColor(TextFormatting.GRAY);
        lines.add(expiry);
        ITextComponent buttons = new TextComponentString("");
        buttons.appendSibling(action(undo ? "confirm_undo" : "confirm_clear", confirmation(token, undo, backupId), TextFormatting.RED));
        buttons.appendText("  ").appendSibling(action("preview_cancel", "/orespawn clearchunk cancel " + token, TextFormatting.GRAY));
        buttons.appendText("  ").appendSibling(details(id));
        lines.add(buttons);
        return lines;
    }

    public static void preview(AdminSource sender, String id, String token, boolean undo, String backupId,
            String dimension, int cx, int cz, int minY, int maxY, int changes, int preserved) {
        for (ITextComponent line : previewLines(id, token, undo, backupId, dimension, cx, cz, minY, maxY, changes, preserved)) sender.sendMessage(line);
        consoleCommand(sender, confirmation(token, undo, backupId));
    }

    static void started(AdminSource sender, String id) {
        sender.sendMessage(new TextComponentTranslation("commands.orespawn.started", details(id)));
        consoleCommand(sender, "/orespawn jobs " + id);
    }

    static void completed(AdminSource sender, String id, int edited, boolean undo, String backupId, String dimension) {
        if (undo) sender.sendMessage(new TextComponentTranslation("commands.orespawn.complete_undo", edited, details(id)));
        else {
            String command = "/orespawn undo " + backupId + " --dimension " + dimension;
            sender.sendMessage(new TextComponentTranslation("commands.orespawn.complete_clear", edited,
                    action("undo_preview", command, TextFormatting.AQUA), details(id)));
            consoleCommand(sender, command);
        }
    }

    static void result(AdminSource sender, String id, String status, String message) {
        String shortMessage = message.length() > 1024 ? message.substring(0, 1021) + "..." : message;
        sender.sendMessage(new TextComponentTranslation("commands.orespawn.job_result", details(id), status, shortMessage));
        consoleCommand(sender, "/orespawn jobs " + id);
    }

    private static void consoleCommand(AdminSource sender, String command) {
        // Server consoles cannot click chat, so they still need a copyable command.
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) sender.sendMessage(new TextComponentString(command));
    }

    private static ITextComponent dimensionLabel(String dimension) {
        if ("minecraft:overworld".equals(dimension) || "minecraft:the_nether".equals(dimension) || "minecraft:the_end".equals(dimension))
            return new TextComponentTranslation("value.orespawn.dimension." + dimension.substring("minecraft:".length()));
        return new TextComponentString(dimension);
    }
}
