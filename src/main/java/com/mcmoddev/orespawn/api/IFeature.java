package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;

import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

public interface IFeature {
	void generate( World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
	               GeneratorParameters parameters );
	
	void setRandom(Random rand);
	
	JsonObject getDefaultParameters();
}
