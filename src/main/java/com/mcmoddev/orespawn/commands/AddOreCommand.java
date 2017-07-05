package com.mcmoddev.orespawn.commands;

import com.google.gson.*;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
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

public class AddOreCommand extends CommandBase {
	private static final String BLOCK = "block";
	private static final String STATE2 = "state";
	private static final String ALL = "all";
	
    @Override
    public String getName() {
        return "addore";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/addore <file> <dimension|all>";
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
        } else if (args.length != 2) {
            throw new CommandException(this.getUsage(sender));
        }
        
        String file = args[0];
        @SuppressWarnings("deprecation")
        IBlockState state = ((ItemBlock) stack.getItem()).getBlock().getStateFromMeta(stack.getItemDamage());

        int dimension = OreSpawn.API.dimensionWildcard();

        try {
            if (!args[1].equals(ALL)) {
                dimension = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
            throw new CommandException(args[1] + " isn't a valid dimension");
        }

        JsonObject ore = new JsonObject();
        ore.addProperty(BLOCK, state.getBlock().getRegistryName().toString());
        ore.addProperty(STATE2, StateUtil.serializeState(state));
        ore.addProperty("size", 25);
        ore.addProperty("variation", 12);
        ore.addProperty("frequency", 20);
        ore.addProperty("min_height", 0);
        ore.addProperty("max_height", 128);

        this.putFile(file, ore, dimension);

        player.sendStatusMessage(new TextComponentString("Added " + state.getBlock().getRegistryName().toString() + " to the json"), true);
    }

    private void putFile(String file, JsonObject ore, int id) {
    	DimensionBuilder db = OreSpawn.API.getLogic(file).newDimensionBuilder(id);
    	SpawnBuilder sb = db.newSpawnBuilder(null);
    	OreBuilder ob = sb.newOreBuilder();
    	String b = ore.get(BLOCK).getAsString();
    	ore.remove(BLOCK);
    	String s = ore.get(STATE2).getAsString();
    	ore.remove(STATE2);
    	if( "normal".equals(s) ) {
    		ob.setOre(b);
    	} else {
    		ob.setOre(b, s);
    	}
    	FeatureBuilder fb = sb.newFeatureBuilder("default");
    	fb.setGenerator("default").setDefaultParameters().setParameters(ore);
    	BiomeBuilder bb = sb.newBiomeBuilder();
    	IBlockState rep = ReplacementsRegistry.getDimensionDefault(id);
    	List<IBlockState> rl = new ArrayList<>();
    	rl.add(rep);
    	sb.create(bb, fb, rl, ob);
    	db.create(sb);
    	OreSpawn.API.getLogic(file).create(db);
    	OreSpawn.API.registerLogic(OreSpawn.API.getLogic(file));
	}

    @Override
    public int compareTo(ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
