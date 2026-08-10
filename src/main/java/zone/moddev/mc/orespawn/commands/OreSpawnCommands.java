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
	}

	private static final class RootCommand extends CommandBase {
		@Override public String getName() { return "orespawn"; }
		@Override public String getUsage(ICommandSender sender) {
			return "/orespawn <status|reload|retrogen [radius]|dump-biomes>";
		}
		@Override public int getRequiredPermissionLevel() { return 0; }

		@Override
		public void execute(MinecraftServer server, ICommandSender sender, String[] args)
				throws CommandException {
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
