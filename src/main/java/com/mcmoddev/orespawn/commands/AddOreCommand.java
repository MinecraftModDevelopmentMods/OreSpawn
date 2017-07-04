package com.mcmoddev.orespawn.commands;

import com.google.gson.*;
import com.mcmoddev.orespawn.OreSpawn;
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
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;
import java.io.IOException;

public class AddOreCommand extends CommandBase {
	private static final String dim = "dimension";
	private static final String all = "all";
	
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

        File file = new File(".", "orespawn" + File.separator + args[0] + ".json");
        JsonParser parser = new JsonParser();
        @SuppressWarnings("deprecation")
        IBlockState state = ((ItemBlock) stack.getItem()).getBlock().getStateFromMeta(stack.getItemDamage());

        if (!file.exists()) {
            throw new CommandException("That file doesn't exist" + (args[0].endsWith(".json") ? " (don't add .json)" : ""));
        }

        int dimension = OreSpawn.API.dimensionWildcard();

        try {
            if (!args[1].equals(all)) {
                dimension = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
            throw new CommandException(args[1] + " isn't a valid dimension");
        }

        JsonObject ore = new JsonObject();
        ore.addProperty("block", state.getBlock().getRegistryName().toString());
        ore.addProperty("state", StateUtil.serializeState(state));
        ore.addProperty("size", 25);
        ore.addProperty("variation", 12);
        ore.addProperty("frequency", 20);
        ore.addProperty("min_height", 0);
        ore.addProperty("max_height", 128);

        try {
            JsonArray json = parser.parse(FileUtils.readFileToString(file)).getAsJsonArray();

            for (JsonElement element : json) {
                JsonObject object = element.getAsJsonObject();

                if (object.has(dim) ? dimension == object.get(dim).getAsInt() : dimension == OreSpawn.API.dimensionWildcard()) {
                    object.get("ores").getAsJsonArray().add(ore);
                    this.saveFile(json, file);

                    return;
                }
            }

            JsonObject object = new JsonObject();

            if (dimension != OreSpawn.API.dimensionWildcard()) {
                object.addProperty(dim, dimension);
            }

            JsonArray array = new JsonArray();
            array.add(ore);
            object.add("ores", array);

            this.saveFile(json, file);
        } catch (IOException e) {
            throw new CommandException("Something went wrong - "+e.getMessage());
        }

        player.sendStatusMessage(new TextComponentString("Added " + state.getBlock().getRegistryName().toString() + " to the json"), true);
    }

    private void saveFile(JsonArray array, File file) throws CommandException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(array);

        try {
            FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("Something went wrong - "+e.getMessage());
        }
    }

    @Override
    public int compareTo(ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
