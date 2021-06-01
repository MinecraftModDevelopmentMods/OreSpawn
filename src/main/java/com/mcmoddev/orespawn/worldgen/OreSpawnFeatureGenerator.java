package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.api.os3.OS3FeatureGenerator;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraft.util.math.ChunkPos;

public class OreSpawnFeatureGenerator implements IWorldGenerator, OS3FeatureGenerator {
	private final ISpawnEntry spawn;
	private final String name;
	
	public OreSpawnFeatureGenerator( final ISpawnEntry spawn, final String name ) {
		this.spawn = spawn;
		this.name = name;
	}
	
	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider) {
		final int thisDim = world.provider.getDimension();

		if ((Config.getBoolean(Constants.RETROGEN_KEY) && 
				((this.spawn.isRetrogen() || Config.getBoolean(Constants.FORCE_RETROGEN_KEY)))) || 
				(this.spawn.isEnabled() && this.spawn.dimensionAllowed(thisDim))) {
				this.spawn.generate(random, world, chunkGenerator, chunkProvider, new ChunkPos(chunkX, chunkZ));
		}
	}

	@Override
	public ISpawnEntry getSpawnData() {
		return this.spawn;
	}

	@Override
	public String getSpawnName() {
		return this.name;
	}
}
