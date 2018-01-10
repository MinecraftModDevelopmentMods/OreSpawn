package com.mcmoddev.orespawn.api;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.util.OreList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.ChunkCoordIntPair;

import java.util.List;
import java.util.Map;

public class GeneratorParameters {

	private final ChunkCoordIntPair chunk;
	private final OreList ores;
	private final ImmutableList<IBlockState> replacements;
	private final JsonObject parameters;
	private final BiomeLocation biomes;

	public GeneratorParameters(ChunkCoordIntPair chunkPos, OreList oreList, List<IBlockState> replacementBlocks,
	    BiomeLocation biomes, JsonObject generatorParameters) {
		this.parameters = new JsonObject();

		for (Map.Entry<String, JsonElement> stringJsonElementEntry : generatorParameters.entrySet()) {
			this.parameters.add(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue());
		}

		this.chunk = new ChunkCoordIntPair(chunkPos.chunkXPos, chunkPos.chunkZPos);
		this.ores = oreList;
		this.replacements = ImmutableList.copyOf(replacementBlocks);
		this.biomes = biomes;
	}

	public ChunkCoordIntPair getChunk() {
		return chunk;
	}

	public BiomeLocation getBiomes() {
		return biomes;
	}

	public ImmutableList<IBlockState> getReplacements() {
		return replacements;
	}

	public JsonObject getParameters() {
		return parameters;
	}

	public OreList getOres() {
		return ores;
	}

}
