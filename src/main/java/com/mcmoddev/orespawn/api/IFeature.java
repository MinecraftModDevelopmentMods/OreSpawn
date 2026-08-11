package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Compatibility union. Each historical generate descriptor is a default so a
 * binary compiled for the other OS3 generation can still be loaded safely.
 */
public interface IFeature {
	default void generate(World world, IChunkGenerator generator, IChunkProvider provider,
			GeneratorParameters parameters) {
		throw new UnsupportedOperationException("OS3 3.2 feature entry point is not implemented");
	}

	default void generate(World world, IChunkGenerator generator, IChunkProvider provider,
			ISpawnEntry spawn, ChunkPos pos) {
		throw new UnsupportedOperationException("OS3 3.3 feature entry point is not implemented");
	}

	void setRandom(Random random);

	JsonObject getDefaultParameters();

}
