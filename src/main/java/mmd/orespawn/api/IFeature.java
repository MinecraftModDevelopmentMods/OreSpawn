package mmd.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;

public interface IFeature {
	void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider, JsonObject parameters, IBlockState block );
}
