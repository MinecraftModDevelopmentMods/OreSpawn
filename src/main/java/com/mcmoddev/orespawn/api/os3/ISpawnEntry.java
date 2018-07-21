package com.mcmoddev.orespawn.api.os3;

import java.util.Random;

import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public interface ISpawnEntry {

	default boolean isEnabled() {
		return false;
	}

	default boolean isRetrogen() {
		return false;
	}

	String getSpawnName();

	boolean dimensionAllowed(int dimension);

	boolean biomeAllowed(ResourceLocation biomeName);

	boolean biomeAllowed(Biome biome);

	IFeatureEntry getFeature();

	OreSpawnBlockMatcher getMatcher();

	IBlockList getBlocks();

	void generate(Random random, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, ChunkPos pos);

	IDimensionList getDimensions();

	BiomeLocation getBiomes();
}
