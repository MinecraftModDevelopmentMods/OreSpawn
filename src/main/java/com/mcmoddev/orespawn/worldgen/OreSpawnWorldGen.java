package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

public class OreSpawnWorldGen implements IWorldGenerator {

	@Override
	public void generate(final Random random, final int chunkX, final int chunkZ, final World world,
			final IChunkGenerator chunkGenerator, final IChunkProvider chunkProvider) {

		final int thisDim = world.provider.getDimension();

		OreSpawn.API.getSpawns(thisDim).stream().filter(ISpawnEntry::isEnabled)
				.filter(sb -> !Config.getBoolean(Constants.RETROGEN_KEY)
						|| (sb.isRetrogen() || Config.getBoolean(Constants.FORCE_RETROGEN_KEY)))
				.forEach(spawn -> 
					spawn.generate(random, world, chunkGenerator, chunkProvider,
							new ChunkPos(chunkX, chunkZ)));
	}
}
