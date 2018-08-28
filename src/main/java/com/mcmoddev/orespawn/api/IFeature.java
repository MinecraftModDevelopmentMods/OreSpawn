package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.registries.IForgeRegistryEntry;

public interface IFeature extends IForgeRegistryEntry<IFeature> {

    void generate(World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
            ISpawnEntry spawn, ChunkPos pos);

    void setRandom(Random rand);

    JsonObject getDefaultParameters();
}
