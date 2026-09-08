package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Deprecated OS3 ABI shell. OreSpawn 4's coordinator owns flat-bedrock and
 * retrogen scheduling, so this class deliberately cannot create a second pass.
 */
@Deprecated
public class FlatBedrock implements IWorldGenerator {
	@Override public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) { }
	public void retrogen(World world, int chunkX, int chunkZ) { }
}
