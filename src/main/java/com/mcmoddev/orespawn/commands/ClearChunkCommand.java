package com.mcmoddev.orespawn.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class ClearChunkCommand extends CommandBase {
    @Override
    public String getName() {
        return "clearchunk";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/clearchunk <viewores|dirtandgravel|classic>";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException("Only players can use this command");
        }

        EntityPlayer player = (EntityPlayer) sender;
        Chunk chunk = player.getEntityWorld().getChunkFromBlockCoords(player.getPosition());
        ChunkPos chunkPos = chunk.getPos();
        List<Block> blocks;
        
        boolean flagClassic = false;
        
        if( args.length > 0 ) {
    		List<String> blockNames;
        	switch( args[0].toLowerCase() ) {
        	case "viewores":
        		blockNames = Arrays.asList("minecraft:stone", "minecraft:diorite", "minecraft:andesite", "minecraft:granite", "minecraft:dirt",
        								   "minecraft:grass", "minecraft:gravel", "minecraft:water", "minecraft:lava", "minecraft:sandstone",
        								   "minecraft:red_sandstone", "minecraft:sand");
        		break;
        	case "dirtandgravel":
        		blockNames = Arrays.asList("minecraft:stone", "minecraft:gravel", "minecraft:dirt", "minecraft:grass", "minecraft:water", "minecraft:lava");
        		break;
        	case "classic":
        		blockNames = OreSpawnWorldGen.getSpawnBlocks().stream().map( block -> block.getRegistryName().toString() ).collect(Collectors.toList());
        		blockNames.add("minecraft:water");
        		blockNames.add("minecraft:lava");
        		flagClassic = true;
        		break;
        	default:
        		blockNames = Arrays.asList("minecraft:stone", "minecraft:water", "minecraft:lava");
        	}
        	blocks = blockNames.stream().map( blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName))).collect(Collectors.toList());
        } else {
        	blocks = Arrays.asList("minecraft:stone", "minecraft:water", "minecraft:lava").stream()
        		  .map( blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName))).collect(Collectors.toList());
        }
        
        List<Block> overburden = Arrays.asList("minecraft:dirt", "minecraft:sand", "minecraft:gravel", "minecraft:grass", "minecraft:sandstone", "minecraft:red_sandstone").stream()
      		  .map( blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName))).collect(Collectors.toList());        		  

        for (int x = chunkPos.getXStart(); x <= chunkPos.getXEnd(); x++) {
            for (int y = 256; y >= 0; y--) {
                for (int z = chunkPos.getZStart(); z <= chunkPos.getZEnd(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = player.world.getBlockState(pos).getBlock();
                    if (blocks.contains(block) || ((y >= 64 && overburden.contains(block) ) && !flagClassic)) {
                        player.world.setBlockToAir(pos);
                    }
                }
            }
        }
        if( flagClassic ) flagClassic = false;
        
        player.sendStatusMessage(new TextComponentString("chunk "+chunkPos.toString()+" cleared"), true);
    }

    @Override
    public int compareTo(ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
