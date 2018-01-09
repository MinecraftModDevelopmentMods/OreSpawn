package com.mcmoddev.orespawn.commands;

import com.google.gson.*;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.util.StateUtil;
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
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class AddOreCommand extends CommandBase {
	private static final String ALL = "all";

	@Override
	public String getCommandName() {
		return "addore";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/addore <file> <dimension|all> <options>";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (!(sender instanceof EntityPlayer)) {
			throw new CommandException("Only players can use this command");
		}

		EntityPlayer player = (EntityPlayer) sender;
		ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
		String jsonArgs = null;

		if (stack == null) {
			throw new CommandException("You have no item in your main hand");
		} else if (!(stack.getItem() instanceof ItemBlock)) {
			throw new CommandException("The item in your main hand isn't a block");
		} else if (args.length < 2) {
			throw new CommandException(this.getCommandUsage(sender));
		} else if (args.length > 2) {
			jsonArgs = getChatComponentFromNthArg(sender, args, 2).getUnformattedText();
		}

		String file = args[0];
		@SuppressWarnings("deprecation")
		IBlockState state = ((ItemBlock) stack.getItem()).getBlock().getStateFromMeta(stack.getItemDamage());

		int dimension = OreSpawn.API.dimensionWildcard();

		try {
			if (!args[1].equalsIgnoreCase(ALL)) {
				dimension = Integer.parseInt(args[1]);
			}
		} catch (NumberFormatException e) {
			throw new CommandException(args[1] + " isn't a valid dimension");
		}


		JsonObject ore = new JsonObject();
		JsonObject oreArgs = null;
		int size = 25;
		int variation = 12;
		int frequency = 20;
		int minHeight = 0;
		int maxHeight = 128;

		oreArgs = new JsonObject();
		oreArgs.addProperty(ConfigNames.DefaultFeatureProperties.SIZE, size);
		oreArgs.addProperty(ConfigNames.DefaultFeatureProperties.VARIATION, variation);
		oreArgs.addProperty(ConfigNames.DefaultFeatureProperties.FREQUENCY, frequency);
		oreArgs.addProperty(ConfigNames.DefaultFeatureProperties.MINHEIGHT, minHeight);
		oreArgs.addProperty(ConfigNames.DefaultFeatureProperties.MAXHEIGHT, maxHeight);
		ore.addProperty(ConfigNames.BLOCK, state.getBlock().getRegistryName().toString());
		ore.addProperty(ConfigNames.STATE, StateUtil.serializeState(state));

		if (jsonArgs != null) {
			JsonObject newOreArgs = (new JsonParser()).parse(jsonArgs).getAsJsonObject();
			setProperties(oreArgs, newOreArgs);
		}

		setOre(ore, oreArgs);

		this.putFile(file, ore, dimension);

		player.addChatComponentMessage(new TextComponentString("Added " + state.getBlock().getRegistryName().toString() + " to the json"));
	}

	private void setProperties(JsonObject oreArgs, JsonObject newOreArgs) {
		for (Entry<String, JsonElement> ent : newOreArgs.entrySet()) {
			oreArgs.remove(ent.getKey());
			oreArgs.add(ent.getKey(), ent.getValue());
		}
	}

	private void setOre(JsonObject ore, JsonObject oreArgs) {
		for (Entry<String, JsonElement> ent : oreArgs.entrySet()) {
			ore.add(ent.getKey(), ent.getValue());
		}
	}

	private void putFile(String file, JsonObject ore, int id) {
		DimensionBuilder db = OreSpawn.API.getLogic(file).newDimensionBuilder(id);
		SpawnBuilder sb = db.newSpawnBuilder(null);
		OreBuilder ob = sb.newOreBuilder();
		String b = ore.get(ConfigNames.BLOCK).getAsString();
		ore.remove(ConfigNames.BLOCK);
		String s = ore.get(ConfigNames.STATE).getAsString();
		ore.remove(ConfigNames.STATE);

		if (ConfigNames.STATE_NORMAL.equals(s)) {
			ob.setOre(b);
		} else {
			ob.setOre(b, s);
		}

		FeatureBuilder fb = sb.newFeatureBuilder(ConfigNames.DEFAULT);
		fb.setGenerator(ConfigNames.DEFAULT).setDefaultParameters().setParameters(ore);
		BiomeBuilder bb = sb.newBiomeBuilder();
		IBlockState rep = ReplacementsRegistry.getDimensionDefault(id).get(0);
		List<IBlockState> rl = new ArrayList<>();
		rl.add(rep);
		sb.create(bb, fb, rl, ob);
		db.create(sb);
		OreSpawn.API.getLogic(file).create(db);
		OreSpawn.API.registerLogic(OreSpawn.API.getLogic(file));
		OreSpawn.writer.writeAddOreEntry(file);
	}

	@Override
	public int compareTo(ICommand command) {
		return this.getCommandName().compareTo(command.getCommandName());
	}
}
