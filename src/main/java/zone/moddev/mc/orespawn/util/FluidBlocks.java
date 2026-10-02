package zone.moddev.mc.orespawn.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.fluid.IFluidState;

/** Distinguishes a fluid's own block from a waterlogged block. */
public final class FluidBlocks {
	private FluidBlocks() { }

	public static boolean isFluidBlock(Block block) {
		if (block == null || block == Blocks.AIR) return false;
		// Fluid blocks are identifiable even before their fluid holders are populated.
		if (block instanceof FlowingFluidBlock) return true;
		IFluidState fluid = block.getDefaultState().getFluidState();
		return !fluid.isEmpty() && fluid.getBlockState().getBlock() == block;
	}
}
