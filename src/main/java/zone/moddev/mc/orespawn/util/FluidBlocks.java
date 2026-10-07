package zone.moddev.mc.orespawn.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;

/** Distinguishes a fluid's own block from a waterlogged block. */
public final class FluidBlocks {
	private FluidBlocks() { }

	public static boolean isFluidBlock(Block block) {
		if (block == null || block == Blocks.AIR) return false;
		// Fluid blocks are identifiable even before their fluid holders are populated.
		if (block instanceof LiquidBlock) return true;
		FluidState fluid = block.defaultBlockState().getFluidState();
		return !fluid.isEmpty() && fluid.createLegacyBlock().getBlock() == block;
	}
}
