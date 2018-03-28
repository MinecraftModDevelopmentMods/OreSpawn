package com.mcmoddev.orespawn.commands;

import com.google.gson.*;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.json.OreSpawnReader;
import com.mcmoddev.orespawn.json.OreSpawnWriter;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;

import org.apache.commons.io.FilenameUtils;

public class AddOreCommand extends CommandBase {

	@Override
	public String getName() {
		return "addore";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/addore <file> <options - json data>";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (!(sender instanceof EntityPlayer)) {
			throw new CommandException("Only players can use this command");
		}

		EntityPlayer player = (EntityPlayer) sender;
		ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);

		if (stack == null) {
			throw new CommandException("You have no item in your main hand");
		} else if (!(stack.getItem() instanceof ItemBlock)) {
			throw new CommandException("The item in your main hand isn't a block");
		} else if (args.length < 2) {
			throw new CommandException(this.getUsage(sender));
		}

		String file = args[0];
		@SuppressWarnings("deprecation")
		IBlockState state = Block.getBlockFromItem(stack.getItem()).getStateFromMeta(stack.getItemDamage());

		String rawData = getChatComponentFromNthArg(sender, args, 1).getUnformattedText();
		JsonParser p = new JsonParser();
		JsonElement parsed = mergeDefaults(p.parse(rawData), state);
		OreSpawnReader.loadFromJson(FilenameUtils.getBaseName(file), parsed);
		OreSpawnWriter.saveSingle(FilenameUtils.getBaseName(file));
	}
	
	private JsonElement mergeDefaults(JsonElement parse, IBlockState state) {
		JsonObject work = parse.getAsJsonObject();
		JsonObject emptyBlacklist = new JsonObject();
		emptyBlacklist.add("excludes", new JsonArray());
		
		if(!work.has(ConfigNames.ENABLED)) work.addProperty(ConfigNames.ENABLED, true);
		if(!work.has(ConfigNames.RETROGEN)) work.addProperty(ConfigNames.RETROGEN, false);
		if(!work.has(ConfigNames.FEATURE)) work.addProperty(ConfigNames.FEATURE, "default");
		if(!work.has(ConfigNames.REPLACEMENT)) work.addProperty(ConfigNames.REPLACEMENT, "default");
		if(!work.has(ConfigNames.PARAMETERS)) work.add(ConfigNames.PARAMETERS, 
				OreSpawn.API.getFeature(work.get(ConfigNames.FEATURE).getAsString()).getDefaultParameters());
		if(!work.has(ConfigNames.DIMENSIONS)) work.add(ConfigNames.DIMENSIONS, emptyBlacklist);
		if(!work.has(ConfigNames.BIOMES)) work.add(ConfigNames.BIOMES, emptyBlacklist);
		
		JsonObject block = new JsonObject();
		block.addProperty(ConfigNames.CHANCE, 100);
		block.addProperty(ConfigNames.NAME, state.getBlock().getRegistryName().toString());
		block.addProperty(ConfigNames.STATE, StateUtil.serializeState(state));
		work.add(ConfigNames.BLOCK, block);
		
		return work;
	}

	@Override
	public int compareTo(ICommand command) {
		return this.getName().compareTo(command.getName());
	}
}
