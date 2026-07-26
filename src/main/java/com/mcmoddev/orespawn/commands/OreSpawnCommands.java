package com.mcmoddev.orespawn.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;
import com.mcmoddev.orespawn.worldgen.OreRetrogenManager;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfile;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfileManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-side diagnostics and bounded maintenance commands. */
public final class OreSpawnCommands {
	private OreSpawnCommands() {
	}

	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("orespawn")
				.then(Commands.literal("status").executes(context -> status(context.getSource())))
				.then(Commands.literal("reload").requires(source -> source.hasPermission(2))
						.executes(context -> reload(context.getSource())))
				.then(Commands.literal("retrogen").requires(source -> source.hasPermission(2))
						.executes(context -> retrogen(context.getSource(), 0))
						.then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
								.executes(context -> retrogen(context.getSource(),
										IntegerArgumentType.getInteger(context, "radius")))))
				.then(Commands.literal("dump-biomes").requires(source -> source.hasPermission(2))
						.executes(context -> dumpBiomes(context.getSource()))));
	}

	private static int status(net.minecraft.commands.CommandSourceStack source) {
		WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
		String providers = String.join(", ", WorldgenIntegrationManager.activeProviderIds());
		if (providers.isEmpty()) providers = "none";
		source.sendSuccess(Component.literal("OreSpawn 4: mode=" + profile.geologyMode().name().toLowerCase()
				+ ", providers=" + providers + ", queued_retrogen=" + OreRetrogenManager.queuedCount()), false);
		return 1;
	}

	private static int reload(net.minecraft.commands.CommandSourceStack source) {
		if (WorldGeologyProfileManager.reloadActiveProfile()) {
			source.sendSuccess(Component.literal("Reloaded this world's OreSpawn profile."), true);
			return 1;
		}
		source.sendFailure(Component.literal("No active OreSpawn world profile could be reloaded."));
		return 0;
	}

	private static int retrogen(net.minecraft.commands.CommandSourceStack source, int radius) {
		ChunkPos center = new ChunkPos((int) Math.floor(source.getPosition().x) >> 4,
				(int) Math.floor(source.getPosition().z) >> 4);
		int queued = OreRetrogenManager.queueLoadedArea(source.getLevel(), center, radius);
		source.sendSuccess(Component.literal("Queued " + queued
				+ " loaded chunk(s) for OreSpawn retrogen."), true);
		return queued;
	}

	private static int dumpBiomes(net.minecraft.commands.CommandSourceStack source) {
		List<String> ids = new ArrayList<>();
		for (ResourceLocation id : ForgeRegistries.BIOMES.getKeys()) ids.add(id.toString());
		Collections.sort(ids);
		Path target = FMLPaths.CONFIGDIR.get().resolve("orespawn-biomes.txt");
		try {
			Files.write(target, ids, StandardCharsets.UTF_8);
			source.sendSuccess(Component.literal("Wrote " + ids.size() + " biome IDs to " + target), false);
			return ids.size();
		} catch (IOException e) {
			source.sendFailure(Component.literal("Could not write " + target + ": " + e.getMessage()));
			return 0;
		}
	}
}
