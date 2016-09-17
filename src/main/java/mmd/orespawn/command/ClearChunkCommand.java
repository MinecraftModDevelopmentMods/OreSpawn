package mmd.orespawn.command;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

public class ClearChunkCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "clearchunk";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/clearchunk";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayer)) {
            throw new CommandException("Only players can use this command");
        }

        EntityPlayer player = (EntityPlayer) sender;
        Chunk chunk = player.getEntityWorld().getChunkFromBlockCoords(player.getPosition());
        ChunkPos chunkPos = chunk.getChunkCoordIntPair();

        for (int x = chunkPos.getXStart(); x <= chunkPos.getXEnd(); x++) {
            for (int y = 0; y < 256; y++) {
                for (int z = chunkPos.getZStart(); z <= chunkPos.getZEnd(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = player.worldObj.getBlockState(pos).getBlock();

                    if (block == Blocks.STONE || block == Blocks.DIRT || block == Blocks.GRASS || block == Blocks.SAND || block == Blocks.SANDSTONE || block == Blocks.BEDROCK) {
                        player.worldObj.setBlockToAir(pos);
                    }
                }
            }
        }
    }
}
