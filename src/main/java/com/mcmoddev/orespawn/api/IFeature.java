package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.mcmoddev.orespawn.data.DefaultOregenParameters;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

public interface IFeature {
	void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, DefaultOregenParameters p);
}
