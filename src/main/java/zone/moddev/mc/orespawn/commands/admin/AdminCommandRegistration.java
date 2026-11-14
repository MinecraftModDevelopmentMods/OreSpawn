package zone.moddev.mc.orespawn.commands.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Adds diagnostics without replacing the existing status, reload or explicit retrogen commands. */
public final class AdminCommandRegistration {
    private AdminCommandRegistration() { }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Commands.literal("orespawn");
        for (String action : new String[]{"help", "chunk", "survey", "explain", "jobs", "cancel", "clearchunk", "undo"}) {
            int permission = action.equals("clearchunk") || action.equals("undo") ? 4 : 2;
            root.then(command(action, action, permission));
        }
        root.then(Commands.literal("status").then(command("verbose", "status verbose", 2)));
        root.then(Commands.literal("retrogen").requires(source -> source.hasPermissionLevel(2))
                .then(command("status", "retrogen status", 2)));
        dispatcher.register(root);
        for (String alias : new String[]{"clearchunk", "clearchunks"}) {
            if (dispatcher.getRoot().getChild(alias) == null)
                dispatcher.register(command(alias, "clearchunk", 4));
        }
    }

    private static LiteralArgumentBuilder<CommandSource> command(String literal, String action, int permission) {
        return Commands.literal(literal).requires(source -> source.hasPermissionLevel(permission))
                .executes(context -> execute(context.getSource(), action))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemaining();
                            int split = remaining.lastIndexOf(' ');
                            com.mojang.brigadier.suggestion.SuggestionsBuilder tail = builder.createOffset(
                                    builder.getStart() + split + 1);
                            java.util.Set<String> choices = new java.util.TreeSet<>(java.util.Arrays.asList(
                                    "--dimension", "--center", "--y", "--block", "preview", "confirm", "cancel", "backups"));
                            com.google.gson.JsonObject profile = WorldGeologyProfileManager.activeProfile().rootCopy();
                            if (action.equals("explain") && profile.has("ores"))
                                profile.getAsJsonObject("ores").entrySet().forEach(entry -> choices.add(entry.getKey()));
                            if (remaining.contains("--dimension"))
                                context.getSource().getServer().forgeGetWorldMap().values().forEach(
                                        world -> choices.add(zone.moddev.mc.orespawn.worldgen.WorldIds.dimension(world).toString()));
                            for (String choice : choices)
                                if (choice.startsWith(tail.getRemaining())) tail.suggest(choice);
                            return tail.buildFuture();
                        })
                        .executes(context -> execute(context.getSource(), action + " "
                                + StringArgumentType.getString(context, "arguments"))));
    }

    private static int execute(CommandSource source, String command) throws CommandSyntaxException {
        try {
            return AdminCommands.handle(source.getServer(), AdminSource.from(source), command.split("\\s+")) ? 1 : 0;
        } catch (AdminFailure failure) {
            throw new SimpleCommandExceptionType(failure.message).create();
        }
    }

    public static void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) {
            AdminJobs.pollExports();
            AdminJobs.tick();
        }
    }
    public static void unload(net.minecraftforge.event.world.WorldEvent.Unload event) { AdminJobs.unload(event.getWorld().getWorld()); }
    public static void stopped(net.minecraftforge.fml.event.server.FMLServerStoppedEvent event) {
        AdminJobs.shutdown();
        AdminLighting.clear();
        zone.moddev.mc.orespawn.worldgen.ChunkBookkeeping.clear();
    }
}
