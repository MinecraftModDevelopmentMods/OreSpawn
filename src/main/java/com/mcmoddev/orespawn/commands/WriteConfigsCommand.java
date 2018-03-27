package com.mcmoddev.orespawn.commands;

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
	public String getUsage(ICommandSender sender) {
		return "/osSaveConfigs";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		sender.sendMessage(new TextComponentString("not re-implemented yet, sorry"));
	}

}
