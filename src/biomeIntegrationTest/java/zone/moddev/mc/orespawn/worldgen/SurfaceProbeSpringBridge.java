package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Test-only package bridge for the Forge 1.12 spring compatibility path. */
public final class SurfaceProbeSpringBridge {
	private SurfaceProbeSpringBridge() {
	}

	public static boolean recognizesProviderRock(Block block) {
		return VanillaSpringCompatibility.isProviderRock(block);
	}

	public static boolean placeWater(WorldServer world, BlockPos pos) {
		return VanillaSpringCompatibility.generate(Blocks.FLOWING_WATER,
				world, new Random(0L), pos);
	}

}
