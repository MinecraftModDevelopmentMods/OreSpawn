package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.util.BinaryTree;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

public interface IFeature {
	void generate(ChunkPos pos, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, JsonObject parameters, BinaryTree ores, IBlockState blockReplace );
	
	void setRandom(Random rand);
	
	JsonObject getDefaultParameters();
}
