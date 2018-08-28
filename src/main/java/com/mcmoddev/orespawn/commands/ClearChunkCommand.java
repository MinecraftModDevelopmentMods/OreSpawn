package com.mcmoddev.orespawn.commands;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

public class ClearChunkCommand extends CommandBase {

    private static final String       STONE_ID      = "minecraft:stone";
    private static final List<String> stoneVariants = Arrays.asList(STONE_ID, "minecraft:diorite",
            "minecraft:andesite", "minecraft:granite", "minecraft:sandstone",
            "minecraft:red_sandstone", "minecraft:netherrack", "minecraft:end_stone");
    private static final List<String> baseStones    = Arrays.asList(STONE_ID,
            "minecraft:netherrack", "minecraft:end_stone", "minecraft:cobblestone",
            "minecraft:obsidian", "minecraft:magma", "minecraft:soul_sand");

    private static final List<String> dirtVariants  = Arrays.asList("minecraft:dirt",
            "minecraft:grass");
    private static final List<String> otherVariants = Arrays.asList("minecraft:gravel",
            "minecraft:sand");

    @Override
    public String getName() {
        return "clearchunk";
    }

    @Override
    public String getUsage(final ICommandSender sender) {
        return "/clearchunk <viewores|dirtandgravel|classic>";
    }

    @Override
    public void execute(final MinecraftServer server, final ICommandSender sender,
            final String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException("Only players can use this command");
        }

        final EntityPlayer player = (EntityPlayer) sender;
        final Chunk chunk = player.getEntityWorld().getChunk(player.getPosition());
        final ChunkPos chunkPos = chunk.getPos();
        final List<IBlockState> blocks;

        final boolean flagClassic = args.length > 0
                ? args[0].toLowerCase().equalsIgnoreCase("classic")
                : false;

        final List<String> blockNames = new LinkedList<>();
        getBlocks(args, blockNames);

        blocks = blockNames.stream()
                .map(blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName)))
                .map(block -> block.getDefaultState()).collect(Collectors.toList());

        blocks.addAll(OreSpawn.API
                .getDimensionDefaultReplacements(player.getEntityWorld().provider.getDimension())
                .stream().collect(Collectors.toList()));
        final List<IBlockState> overburden = Arrays
                .asList("minecraft:dirt", "minecraft:sand", "minecraft:gravel", "minecraft:grass",
                        "minecraft:sandstone", "minecraft:red_sandstone")
                .stream()
                .map(blockName -> ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName)))
                .map(bl -> bl.getDefaultState()).collect(Collectors.toList());

        clearBlocks(chunkPos, blocks, overburden, flagClassic, player);

        player.sendStatusMessage(
                new TextComponentString("chunk " + chunkPos.toString() + " cleared"), true);
    }

    private void clearBlocks(final ChunkPos chunkPos, final List<IBlockState> blocks,
            final List<IBlockState> overburden, final boolean flagClassic,
            final EntityPlayer player) {
        for (int x = chunkPos.getXStart(); x <= chunkPos.getXEnd(); x++) {
            for (int y = 256; y >= 0; y--) {
                for (int z = chunkPos.getZStart(); z <= chunkPos.getZEnd(); z++) {
                    final BlockPos pos = new BlockPos(x, y, z);
                    final IBlockState block = player.getEntityWorld().getBlockState(pos);
                    removeIfBlocks(player, pos, block, blocks, overburden, !flagClassic);
                    removeIfFluid(pos, player);
                }
            }
        }
    }

    private void removeIfFluid(final BlockPos pos, final EntityPlayer player) {
        if (player.getEntityWorld().getBlockState(pos).getMaterial().isLiquid()) {
            final IBlockState bs = player.getEntityWorld().getBlockState(pos);

            if (bs.getMaterial().isLiquid()) {
                player.getEntityWorld().setBlockToAir(pos);
            }
        }
    }

    private void removeIfBlocks(final EntityPlayer player, final BlockPos pos,
            final IBlockState block, final List<IBlockState> blocks,
            final List<IBlockState> overburden, final boolean flagClassic) {
        if (blocks.contains(block)
                || ((pos.getY() >= 64 && overburden.contains(block)) && flagClassic)) {
            player.getEntityWorld().setBlockToAir(pos);
        }
    }

    private void getBlocks(final String[] args, final List<String> blockNames) {
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "viewores":
                    blockNames.addAll(stoneVariants);
                    blockNames.addAll(dirtVariants);
                    blockNames.addAll(otherVariants);
                    break;

                case "dirtandgravel":
                    blockNames.add(STONE_ID);
                    blockNames.addAll(dirtVariants);
                    blockNames.addAll(otherVariants);
                    break;

                case "classic":
                    blockNames.add(Blocks.STONE.getRegistryName().toString());
                    blockNames.add(Blocks.NETHERRACK.getRegistryName().toString());
                    blockNames.add(Blocks.END_STONE.getRegistryName().toString());
                    blockNames
                            .addAll(OreDictionary.getOres("stone").stream().map(ItemStack::getItem)
                                    .map(Block::getBlockFromItem).map(Block::getRegistryName)
                                    .map(ResourceLocation::toString).collect(Collectors.toList()));
                    break;

                default:
                    blockNames.addAll(baseStones);
            }
        } else {
            blockNames.addAll(baseStones);
        }
    }

    @Override
    public int compareTo(final ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
