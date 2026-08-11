package com.mcmoddev.orespawn.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Binary shell for OS3 3.2. It is deliberately never registered: translated
 * entries run through OreSpawn 4's single compatibility scheduler.
 */
public class OreSpawnWorldGen implements IWorldGenerator {
	public OreSpawnWorldGen() { }
	public OreSpawnWorldGen(Map<Integer, ? extends List<?>> dimensions, long seed) { }
	public static ImmutableList<Block> getSpawnBlocks() { return ImmutableList.copyOf(Collections.<Block>emptyList()); }
	@Override public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator generator, IChunkProvider provider) { }
}
