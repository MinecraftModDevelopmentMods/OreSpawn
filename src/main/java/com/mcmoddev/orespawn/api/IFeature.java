package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Compatibility union. Each historical generate descriptor is a default so a
 * binary compiled for the other OS3 generation can still be loaded safely.
 */
public interface IFeature extends IForgeRegistryEntry<IFeature> {
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

	@Override default IFeature setRegistryName(ResourceLocation name) { return this; }
	@Override default ResourceLocation getRegistryName() { return null; }
	@SuppressWarnings("unchecked")
	@Override default Class<IFeature> getRegistryType() { return (Class<IFeature>) (Class<?>) IFeature.class; }
}
