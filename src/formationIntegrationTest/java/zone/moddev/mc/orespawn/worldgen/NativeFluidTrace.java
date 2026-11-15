package zone.moddev.mc.orespawn.worldgen;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Records successful native reaction writes, not guessed proximity to a fluid. */
public final class NativeFluidTrace {
    private static final Map<World, Set<Long>> WRITES = new IdentityHashMap<>();
    private NativeFluidTrace() { }
    public static boolean write(World world, BlockPos pos, IBlockState state) {
        boolean changed = world.setBlockState(pos, state);
        if (changed && state.getBlock() == Blocks.STONE)
            WRITES.computeIfAbsent(world, ignored -> new HashSet<>()).add(pos.toLong());
        return changed;
    }
    public static boolean contains(World world, BlockPos pos) {
        Set<Long> positions = WRITES.get(world);
        return positions != null && positions.contains(pos.toLong());
    }
}
