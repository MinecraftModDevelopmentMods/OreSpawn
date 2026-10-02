package zone.moddev.mc.orespawn.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.init.Blocks;

/** Distinguishes a fluid's own block from a waterlogged block. */
public final class FluidBlocks {
	private FluidBlocks() { }

	public static boolean isFluidBlock(Block block) {
		if (block == null || block == Blocks.AIR) return false;
		IFluidState fluid = block.getDefaultState().getFluidState();
		return !fluid.isEmpty() && (block instanceof BlockFlowingFluid
				|| fluid.getBlockState().getBlock() == block);
	}
}
