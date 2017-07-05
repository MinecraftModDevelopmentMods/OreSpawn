package com.mcmoddev.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;

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

	private final Map<Integer, List<SpawnBuilder>> dimensions;
	public static final List<Block> SPAWN_BLOCKS = new ArrayList<>();

	@SuppressWarnings("unused") private final long nextL;

	public OreSpawnWorldGen(Map<Integer, List<SpawnBuilder>> allDimensions, long nextLong) {
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
		List<SpawnBuilder> entries = this.dimensions.get(thisDim);
		if( entries == null ) {
			// no logic for this dimension, if this is nether or end, just exit
			if( thisDim == -1 || thisDim == 1 ) {
				return;
			}

			entries = this.dimensions.get(OreSpawn.API.dimensionWildcard());
			if( entries == null ) {
				// got complaints about this
				// OreSpawn.LOGGER.fatal("no spawn entries for dimension "+thisDim+" or for all dimensions");
				return;
			}
		} else if( thisDim != -1 && thisDim != 1 
				&& this.dimensions.get(OreSpawn.API.dimensionWildcard()) != null ) {
			entries.addAll(this.dimensions.get(OreSpawn.API.dimensionWildcard()));
		}

		for( SpawnBuilder sE : entries ) {
			Biome biome = world.getBiomeProvider().getBiome(new BlockPos(chunkX*16, 64,chunkZ*16));
			if( sE.getBiomes().matches(biome)) {
				IFeature currentFeatureGen = sE.getFeatureGen().getGenerator();
				IBlockState replacement = sE.getReplacementBlocks().get(0);
				if( replacement == null ) {
					replacement = ReplacementsRegistry.getDimensionDefault(thisDim);
				}
				currentFeatureGen.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider, sE.getFeatureGen().getParameters(), sE.getOres().get(0).getOre(), replacement);
			}
		}
	}
}


