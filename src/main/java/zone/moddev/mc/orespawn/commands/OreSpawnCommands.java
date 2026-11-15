package zone.moddev.mc.orespawn.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.worldgen.OreRetrogenManager;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Server-side diagnostics and bounded maintenance commands. */
public final class OreSpawnCommands {
	private OreSpawnCommands() {
	}

	public static void register(FMLServerStartingEvent event) {
		event.registerServerCommand(new RootCommand());
		for (String alias : new String[]{"clearchunk", "clearchunks"}) {
			if (!event.getServer().getCommandManager().getCommands().containsKey(alias)) event.registerServerCommand(new ClearAlias(alias));
		}
	}

	private static final class ClearAlias extends CommandBase {
		private final String name;
		ClearAlias(String name) { this.name = name; }
		@Override public String getName() { return name; }
		@Override public String getUsage(ICommandSender sender) { return "/orespawn clearchunk [confirm token]"; }
		@Override public int getRequiredPermissionLevel() { return 4; }
		@Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
			String[] delegated = new String[args.length + 1]; delegated[0] = "clearchunk";
			System.arraycopy(args, 0, delegated, 1, args.length);
			zone.moddev.mc.orespawn.commands.admin.AdminCommands.handle(server, sender, delegated);
		}
	}

	private static final class RootCommand extends CommandBase {
		@Override public String getName() { return "orespawn"; }
		@Override public String getUsage(ICommandSender sender) {
			return "/orespawn help";
		}
		@Override public int getRequiredPermissionLevel() { return 0; }

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args)
				throws CommandException {
			if (zone.moddev.mc.orespawn.commands.admin.AdminCommands.handle(server, sender, args)) return;
			String action = args.length == 0 ? "status" : args[0];
			if ("status".equals(action)) {
				status(sender);
			} else if ("reload".equals(action)) {
				requireAdmin(sender);
				reload(sender);
			} else if ("retrogen".equals(action)) {
				requireAdmin(sender);
				int radius = args.length > 1 ? parseInt(args[1], 0, 32) : 0;
				retrogen(sender, radius);
			} else if ("dump-biomes".equals(action)) {
				requireAdmin(sender);
				dumpBiomes(sender);
			} else {
				throw new CommandException(getUsage(sender));
			}
		}

		@Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
				String[] args, net.minecraft.util.math.BlockPos target) {
			if (args.length == 1) {
				List<String> names = new ArrayList<>();
				for (String name : zone.moddev.mc.orespawn.commands.admin.AdminCommands.ACTIONS)
					if ("status".equals(name) || sender.canUseCommand("clearchunk".equals(name) || "undo".equals(name) ? 4 : 2, "orespawn")) names.add(name);
				return getListOfStringsMatchingLastWord(args, names);
			}
			if (!sender.canUseCommand(2, "orespawn")) return Collections.emptyList();
			if (args.length == 2 && "explain".equals(args[0])) {
				List<String> rules = new ArrayList<>();
				com.google.gson.JsonObject root = WorldGeologyProfileManager.activeProfile().toJson();
				if (root.has("ores")) root.getAsJsonObject("ores").entrySet().forEach(e -> rules.add(e.getKey()));
				return getListOfStringsMatchingLastWord(args, rules);
			}
            return getListOfStringsMatchingLastWord(args, "--dimension", "--center", "--y", "--block", "preview", "confirm", "cancel", "status", "verbose", "backups");
		}

		private static void requireAdmin(ICommandSender sender) throws CommandException {
			if (!sender.canUseCommand(2, "orespawn")) throw new CommandException("commands.generic.permission");
		}
	}

	private static void status(ICommandSender sender) {
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		String providers = String.join(", ", WorldgenIntegrationManager.activeProviderIds());
		if (providers.isEmpty()) providers = "none";
		sender.sendMessage(new TextComponentString("OreSpawn 4: mode="
				+ profile.geologyMode().name().toLowerCase() + ", providers=" + providers
				+ ", queued_retrogen=" + OreRetrogenManager.queuedCount()));
	}

	private static void reload(ICommandSender sender) {
		boolean success = WorldGeologyProfileManager.reloadActiveProfile();
		sender.sendMessage(new TextComponentString(success
				? "Reloaded this world's OreSpawn profile."
				: "No active OreSpawn world profile could be reloaded."));
	}

	private static void retrogen(ICommandSender sender, int radius) throws CommandException {
		if (!(sender.getEntityWorld() instanceof WorldServer)) throw new CommandException("No server world");
		WorldServer world = (WorldServer) sender.getEntityWorld();
		ChunkPos center = new ChunkPos(sender.getPosition());
		int queued = OreRetrogenManager.queueLoadedArea(world, center, radius);
		sender.sendMessage(new TextComponentString("Queued " + queued
				+ " loaded chunk(s) for OreSpawn retrogen."));
	}

	private static void dumpBiomes(ICommandSender sender) {
		List<String> ids = new ArrayList<>();
		for (ResourceLocation id : ForgeRegistries.BIOMES.getKeys()) ids.add(id.toString());
		Collections.sort(ids);
		Path target = Loader.instance().getConfigDir().toPath().resolve("orespawn-biomes.txt");
		try {
			Files.write(target, ids, StandardCharsets.UTF_8);
			sender.sendMessage(new TextComponentString("Wrote " + ids.size() + " biome IDs to " + target));
		} catch (IOException e) {
			sender.sendMessage(new TextComponentString("Could not write " + target + ": " + e.getMessage()));
		}
	}
}
