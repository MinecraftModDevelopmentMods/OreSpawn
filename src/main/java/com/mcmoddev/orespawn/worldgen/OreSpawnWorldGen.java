package com.mcmoddev.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.SpawnLogicImpl;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
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

	private final Map<Integer, List<SpawnEntry>> dimensions;
	public static final List<Block> SPAWN_BLOCKS = new ArrayList<>();

	@SuppressWarnings("unused") private final long nextL;

	public OreSpawnWorldGen(Map<Integer, List<SpawnEntry>> allDimensions, long nextLong) {
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
		List<SpawnEntry> entries = this.dimensions.get(thisDim);
		if( entries == null ) {
			// no logic for this dimension, if this is nether or end, just exit
			if( thisDim == -1 || thisDim == 1 ) {
				return;
			}

			entries = this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD);
			if( entries == null ) {
				OreSpawn.LOGGER.fatal("no spawn entries for dimension "+thisDim+" or for all dimensions");
				return;
			}
		} else if( thisDim != -1 && thisDim != 1 ) {
			if( this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD) != null ) {
				entries.addAll(this.dimensions.get(OreSpawnAPI.DIMENSION_WILDCARD));
			}
		}

		for( SpawnEntry sE : entries ) {
			Biome biome = world.getBiomeProvider().getBiome(new BlockPos(chunkX*16, 64,chunkZ*16));
			if( sE.getBiomes().contains(biome) || sE.getBiomes().equals(Collections.<Biome>emptyList()) || sE.getBiomes().isEmpty() ) {
				IFeature currentFeatureGen = sE.getFeatureGen();
				IBlockState replacement = sE.getReplacement();
				if( replacement == null ) {
					replacement = ReplacementsRegistry.getDimensionDefault(thisDim);
				}
				currentFeatureGen.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider, sE.getParameters(), sE.getState(), replacement);
			}
		}
	}
}


