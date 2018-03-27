package com.mcmoddev.orespawn.worldgen;

import java.util.*;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

public class OreSpawnWorldGen implements IWorldGenerator {
	public OreSpawnWorldGen() {
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
	    IChunkProvider chunkProvider) {

		int thisDim = world.provider.getDimension();
		
		OreSpawn.API.getSpawns(thisDim).stream()
		.filter(spawn -> spawn.isEnabled())
		.filter(spawn -> (spawn.isRetrogen() || Config.getBoolean(Constants.FORCE_RETROGEN_KEY)) 
				|| (!Config.getBoolean(Constants.RETROGEN_KEY)))
		.forEach(spawn -> spawn.generate(world, chunkGenerator, chunkProvider, new ChunkPos(chunkX, chunkZ)));
	}
}


