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
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.command.Commands;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-side diagnostics and bounded maintenance commands. */
public final class OreSpawnCommands {
	private OreSpawnCommands() {
	}

	public static void register(FMLServerStartingEvent event) {
		event.getServer().getCommandManager().getDispatcher().register(Commands.literal("orespawn")
				.then(Commands.literal("status").executes(context -> status(context.getSource())))
				.then(Commands.literal("reload").requires(source -> source.hasPermissionLevel(2))
						.executes(context -> reload(context.getSource())))
				.then(Commands.literal("retrogen").requires(source -> source.hasPermissionLevel(2))
						.executes(context -> retrogen(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
								.executes(context -> retrogen(context.getSource(),
										IntegerArgumentType.getInteger(context, "radius")))))
				.then(Commands.literal("dump-biomes").requires(source -> source.hasPermissionLevel(2))
						.executes(context -> dumpBiomes(context.getSource()))));
	}

	private static int status(net.minecraft.command.CommandSource source) {
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		String providers = String.join(", ", WorldgenIntegrationManager.activeProviderIds());
		if (providers.isEmpty()) providers = "none";
		source.sendFeedback(new TextComponentString("OreSpawn 4: mode=" + profile.geologyMode().name().toLowerCase()
				+ ", providers=" + providers + ", queued_retrogen=" + OreRetrogenManager.queuedCount()), false);
		return 1;
	}

	private static int reload(net.minecraft.command.CommandSource source) {
		if (WorldGeologyProfileManager.reloadActiveProfile()) {
			source.sendFeedback(new TextComponentString("Reloaded this world's OreSpawn profile."), true);
			return 1;
		}
		source.sendErrorMessage(new TextComponentString("No active OreSpawn world profile could be reloaded."));
		return 0;
	}

	private static int retrogen(net.minecraft.command.CommandSource source, int radius) {
		ChunkPos center = new ChunkPos((int) Math.floor(source.getPos().x) >> 4,
				(int) Math.floor(source.getPos().z) >> 4);
		int queued = OreRetrogenManager.queueLoadedArea(source.getWorld(), center, radius);
		source.sendFeedback(new TextComponentString("Queued " + queued
				+ " loaded chunk(s) for OreSpawn retrogen."), true);
		return queued;
	}

	private static int dumpBiomes(net.minecraft.command.CommandSource source) {
		List<String> ids = new ArrayList<>();
		for (ResourceLocation id : ForgeRegistries.BIOMES.getKeys()) ids.add(id.toString());
		Collections.sort(ids);
		Path target = FMLPaths.CONFIGDIR.get().resolve("orespawn-biomes.txt");
		try {
			Files.write(target, ids, StandardCharsets.UTF_8);
			source.sendFeedback(new TextComponentString("Wrote " + ids.size() + " biome IDs to " + target), false);
			return ids.size();
		} catch (IOException e) {
			source.sendErrorMessage(new TextComponentString("Could not write " + target + ": " + e.getMessage()));
			return 0;
		}
	}
}
