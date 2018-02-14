package com.mcmoddev.orespawn.commands;

import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;

public class WriteConfigsCommand extends CommandBase {

	@Override
	public String getCommandName() {
		return "osSaveConfigs";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/osSaveConfigs";
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		OreSpawn.writer.writeSpawnEntries();
	}

}
