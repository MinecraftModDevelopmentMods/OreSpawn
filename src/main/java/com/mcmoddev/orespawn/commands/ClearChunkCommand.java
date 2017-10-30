package com.mcmoddev.orespawn.commands;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
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
	private static final String STONE_ID = "minecraft:stone";
	private static final List<String> stoneVariants = Arrays.asList(STONE_ID, "minecraft:diorite", "minecraft:andesite", "minecraft:granite", "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:netherrack", "minecraft:endstone");
	private static final List<String> baseStones = Arrays.asList(STONE_ID, "minecraft:netherrack", "minecraft:endstone", "minecraft:cobblestone", "minecraft:obsidian", "minecraft:magma", "minecraft:soul_sand");
	
	private static final List<String> dirtVariants = Arrays.asList("minecraft:dirt", "minecraft:grass");
	private static final List<String> otherVariants = Arrays.asList("minecraft:gravel", "minecraft:sand");
	
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
        
        boolean flagClassic = args.length>0?args[0].toLowerCase().equalsIgnoreCase("classic"):false;
        
		List<String> blockNames = new LinkedList<>();
		getBlocks(args,blockNames);
		
    	blocks = blockNames.stream()
      		  .map( blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName))).collect(Collectors.toList());
        
        List<Block> overburden = Arrays.asList("minecraft:dirt", "minecraft:sand", "minecraft:gravel", "minecraft:grass", "minecraft:sandstone", "minecraft:red_sandstone").stream()
      		  .map( blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName))).collect(Collectors.toList());        		  

        clearBlocks( chunkPos, blocks, overburden, flagClassic, player);
        
        player.sendStatusMessage(new TextComponentString("chunk "+chunkPos.toString()+" cleared"), true);
    }

    private void clearBlocks(ChunkPos chunkPos, List<Block> blocks, List<Block> overburden, boolean flagClassic, EntityPlayer player) {
        for (int x = chunkPos.getXStart(); x <= chunkPos.getXEnd(); x++) {
            for (int y = 256; y >= 0; y--) {
                for (int z = chunkPos.getZStart(); z <= chunkPos.getZEnd(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = player.world.getBlockState(pos).getBlock();
                    removeIfBlocks( player, pos, block, blocks, overburden, !flagClassic );
                    removeIfFluid( pos, player );
                }
            }
        }
	}

	private void removeIfFluid(BlockPos pos, EntityPlayer player) {
		if( player.world.getBlockState(pos).getMaterial().isLiquid() ) {
        	IBlockState bs = player.world.getBlockState(pos);
        	if( bs.getMaterial().equals(Material.LAVA) || bs.getMaterial().equals(Material.WATER) ) {
                player.world.setBlockToAir(pos);                    		
        	}
        }
	}

	private void removeIfBlocks( EntityPlayer player, BlockPos pos, Block block, List<Block> blocks, List<Block> overburden, boolean flagClassic ) {
        if (blocks.contains(block) || ((pos.getY() >= 64 && overburden.contains(block) ) && flagClassic)) {
            player.world.setBlockToAir(pos);
        } 
	}

	private void getBlocks(String[] args, List<String> blockNames) {
        if( args.length > 0 ) {
        	switch( args[0].toLowerCase() ) {
        	case "viewores":
        		blockNames.addAll(stoneVariants);
        		blockNames.addAll(dirtVariants);
        		blockNames.addAll(otherVariants);
        		break;
        	case "dirtandgravel":
        		blockNames.add("minecraft:stone");
        		blockNames.addAll(dirtVariants);
        		blockNames.addAll(otherVariants);
        		break;
        	case "classic":
        		blockNames.addAll(OreSpawnWorldGen.getSpawnBlocks().stream().map( block -> block.getRegistryName().toString() ).collect(Collectors.toList()));
        		break;
        	default:
        		blockNames.addAll(baseStones);
        	}
        } else {
        	blockNames.addAll(baseStones);
        }
	}

	@Override
    public int compareTo(ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
