package com.mcmoddev.orespawn.commands;

import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.chunk.Chunk;

public class ClearChunkCommand extends CommandBase {
    @Override
    public String getName() {
        return "clearchunk";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/clearchunk";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException("Only players can use this command");
        }

        EntityPlayer player = (EntityPlayer) sender;
        Chunk chunk = player.getEntityWorld().getChunkFromBlockCoords(player.getPosition());
        ChunkPos chunkPos = chunk.getPos();

        for (int x = chunkPos.getXStart(); x <= chunkPos.getXEnd(); x++) {
            for (int y = 0; y < 256; y++) {
                for (int z = chunkPos.getZStart(); z <= chunkPos.getZEnd(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = player.world.getBlockState(pos).getBlock();

                    if (OreSpawnWorldGen.SPAWN_BLOCKS.contains(block)) {
                        player.world.setBlockToAir(pos);
                    }
                }
            }
        }
        player.sendStatusMessage(new TextComponentString("chunk "+chunkPos.toString()+" cleared"), true);
    }

    @Override
    public int compareTo(ICommand command) {
        return this.getName().compareTo(command.getName());
    }
}
