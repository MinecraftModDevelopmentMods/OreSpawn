package com.mcmoddev.orespawn.commands;

import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

public class WriteConfigsCommand extends CommandBase {

	@Override
	public String getName() {
		return "osSaveConfigs";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/osSaveConfigs";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		OreSpawn.writer.writeSpawnEntries();
	}

}
