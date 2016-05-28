package cyano.orespawn.worldgen;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenerator;

import java.util.Random;

public class NonSpawner extends WorldGenerator{

	@Override
	public boolean generate(World worldIn, Random prng, BlockPos coord) {
		// do nothing
		return true;
	}

}
