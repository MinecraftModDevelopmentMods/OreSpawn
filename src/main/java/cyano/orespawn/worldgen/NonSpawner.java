package cyano.orespawn.worldgen;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

/** Deprecated no-op generator retained for OreSpawn 1.x binary linkage. */
@Deprecated
public class NonSpawner extends WorldGenerator {
	@Override public boolean generate(World world, Random random, BlockPos position) { return false; }
}
