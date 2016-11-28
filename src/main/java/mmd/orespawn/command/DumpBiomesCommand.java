package mmd.orespawn.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;
import java.io.IOException;

public class DumpBiomesCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "dumpbiomes";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/dumpbiomes";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        JsonArray array = new JsonArray();

        for (Biome biome : ForgeRegistries.BIOMES) {
            array.add(new JsonPrimitive(biome.getRegistryName().toString()));
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(array);

        try {
            FileUtils.writeStringToFile(new File(".", "biome_dump.json"), StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
        } catch (IOException e) {
            throw new CommandException("Failed to save the json file");
        }

        sender.addChatMessage(new TextComponentString("Done"));
    }

    @Override
    public int compareTo(ICommand command) {
        return this.getCommandName().compareTo(command.getCommandName());
    }
}
