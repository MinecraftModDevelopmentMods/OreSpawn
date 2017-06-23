package com.mcmoddev.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.data.DefaultOregenParameters;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.oredict.OreDictionary;

public class OreSpawnWorldGen implements IWorldGenerator {

	private final Map<Integer, DimensionLogic> dimensions;
	public static final List<Block> SPAWN_BLOCKS = new ArrayList<>();

	@SuppressWarnings("unused") private final long nextL;

	public OreSpawnWorldGen(Map<Integer, DimensionLogic> allDimensions, long nextLong) {
		this.dimensions = Collections.unmodifiableMap(allDimensions);
		this.nextL = nextLong;
		if (SPAWN_BLOCKS.isEmpty()) {
			SPAWN_BLOCKS.add(Blocks.STONE);
			SPAWN_BLOCKS.add(Blocks.NETHERRACK);
			SPAWN_BLOCKS.add(Blocks.END_STONE);
			SPAWN_BLOCKS.addAll(OreDictionary.getOres("stone").stream().filter(stack -> stack.getItem() instanceof ItemBlock).map(stack -> ((ItemBlock) stack.getItem()).getBlock()).collect(Collectors.toList()));
		}
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
			IChunkProvider chunkProvider) {

		int thisDim = world.provider.getDimension();
		DimensionLogic dimensionLogic = this.dimensions.get(thisDim);
		List<SpawnEntry> entries = new ArrayList<>();
		
		if( dimensionLogic == null ) {
			// no logic for this dimension, if this is nether or end, just exit
			if( thisDim == -1 || thisDim == 1 ) {
				return;
			}

			dimensionLogic = this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD);
			if( dimensionLogic == null ) {
				OreSpawn.LOGGER.fatal("no logic for dimension "+thisDim+" or for all dimensions");
				return;
			}
			entries.addAll(dimensionLogic.getEntries());
		} else if( thisDim != -1 && thisDim != 1 ) {
			dimensionLogic = this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD);
			if( dimensionLogic != null ) {
				entries.addAll(dimensionLogic.getEntries());
			}
		}

		for( SpawnEntry sE : entries ) {
			Biome biome = world.getBiomeProvider().getBiome(new BlockPos(chunkX*16, 64,chunkZ*16));
			//			OreSpawn.LOGGER.fatal("Trying to generate in biome "+biome+" for spawn entry with block of type "+sE.getState());
			if( sE.getBiomes().contains(biome) || sE.getBiomes() == Collections.EMPTY_LIST || sE.getBiomes().size() == 0 ) {
				IFeature currentFeatureGen = sE.getFeatureGen();
				// what follows is a stop-gap
				currentFeatureGen.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider, sE.getParameters(), sE.getState());
			}
		}
	}
}


