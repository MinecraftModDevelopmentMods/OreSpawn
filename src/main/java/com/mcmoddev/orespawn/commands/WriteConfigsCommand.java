package com.mcmoddev.orespawn.commands;

import com.mcmoddev.orespawn.json.OreSpawnWriter;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class WriteConfigsCommand extends CommandBase {

	@Override
	public String getName() {
		return "osSaveConfigs";
	}

	@Override
	public String getUsage(final ICommandSender sender) {
		return "/osSaveConfigs";
	}

	@Override
	public void execute(final MinecraftServer server, final ICommandSender sender, final String[] args)
			throws CommandException {
		sender.sendMessage(new TextComponentString(
				"Forcing configs as OreSpawn sees them to be written to disk"));
		OreSpawnWriter.saveConfigs();
	}

}
